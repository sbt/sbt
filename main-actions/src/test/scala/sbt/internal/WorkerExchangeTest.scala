package sbt
package internal

import hedgehog.*
import hedgehog.runner.*
import hedgehog.core.{ ShrinkLimit, SuccessCount }
import hedgehog.core.Result
import org.scalasbt.shadedgson.com.google.gson.JsonParser
import java.net.{ InetAddress, InetSocketAddress, ServerSocket }
import java.util.concurrent.{ CountDownLatch, TimeUnit }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger, AtomicReference }
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.*
import scala.sys.process.Process
import scala.util.control.NonFatal

object WorkerExchangeTest extends Properties:
  given Gen[WorkerConnection] =
    Gen.choice1(Gen.constant(WorkerConnection.Stdio), Gen.constant(WorkerConnection.Tcp))

  def gen[A1: Gen]: Gen[A1] = summon[Gen[A1]]

  override lazy val tests: List[Test] = List(
    propertyN("non-jsonrpc should return exit code 1", propBadInput, 10),
    propertyN("bye should return response json with a result", propBye, 10),
    propertyN("a line for one session is ignored by another session's listener", propDemux, 10),
    propertyN("a request from the worker is not mistaken for the session response", propShape, 10),
    propertyN("a bound connection delivers only to its owner", propBind, 10),
    example("a session registering does not disturb a broadcast in flight", exRegistryChurn),
    example("a broadcast is not held up by what a listener does with it", exBroadcastOutsideLock),
    example("a bound worker's exit reaches its owner and nobody else", exBoundExitGoesToOwner),
    example("closing a worker gives back the port it was listening on", exCloseFreesThePort),
    example("a fork that never connects fails instead of waiting for ever", exForkNeverConnects),
    example("a blank line is a request the worker cannot parse, not a goodbye", exBlankLine),
    example("unregistering a listener that is not registered is a no-op", exUnregisterTwice),
    example("closing a worker stops the JVM waiting on its request", exCloseStopsTheWorker),
    example("the end of a worker's stream is signalled, not waited out", exStreamEndSignalled),
  )

  def exStreamEndSignalled: Result =
    // awaitStreamEnd is what stops a dead worker being judged before its suites have drained, and it
    // gives up after 30 seconds. That bound is a backstop, not the normal path: the thread reading a
    // worker has to signal the end however it finishes, or judging every dead worker pays the full
    // 30 seconds — once per worker, and a spread group forks several.
    val w = WorkerExchange.startWorker(ForkOptions(), Nil, WorkerConnection.Tcp)
    w.println("""{"jsonrpc": "2.0", "method": "bye", "params": {}, "id": 1}""")
    val exitCode = w.blockForExitCode()
    val startedAt = System.nanoTime()
    w.awaitStreamEnd()
    val elapsed = (System.nanoTime() - startedAt).nanos
    w.close()
    Result
      .assert(exitCode == 0)
      // Nowhere near the bound, so a signal that never comes cannot be mistaken for a slow one.
      .and(Result.assert(elapsed < 15.seconds))
      .log(s"exitCode=$exitCode elapsed=$elapsed")

  def exCloseStopsTheWorker: Result =
    // A worker that has connected sits blocked on the one request sbt is going to send it. Closing
    // has to close the connection, not only the listening socket: closing a ServerSocket leaves what
    // it accepted open, so the JVM would wait for a request that never comes and outlive the build —
    // once per worker, and work stealing forks several per group.
    val w = WorkerExchange.startWorker(ForkOptions(), Nil, WorkerConnection.Tcp)
    w.close()
    // A bound, not a timing assertion: the wait can only run out if the close left it connected.
    val deadline = System.currentTimeMillis() + 30000L
    while w.process.isAlive() && System.currentTimeMillis() < deadline do Thread.sleep(50)
    val stopped = !w.process.isAlive()
    // Otherwise this test would leave a stray JVM behind for the rest of the run.
    if !stopped then w.process.destroy()
    Result.assert(stopped).log(s"stopped=$stopped")

  def exUnregisterTwice: Result =
    // runWorker unregisters in a finally, so this runs on the failure paths too. Removing without
    // checking membership takes an index of -1 and throws, which would replace whatever the run was
    // already failing with.
    val stranger = new WorkerResponseListener:
      def apply(line: String): Unit = ()
      def notifyExit(p: Process): Unit = ()
    WorkerExchange.registerListener(stranger)
    val first = scala.util.Try(WorkerExchange.unregisterListener(stranger))
    val second = scala.util.Try(WorkerExchange.unregisterListener(stranger))
    Result
      .assert(first.isSuccess)
      .and(Result.assert(second.isSuccess))
      .and(Result.assert(!WorkerExchange.listeners.contains(stranger)))
      .log(s"first=$first second=$second")

  def exBlankLine: Result =
    // The worker parks on one request handed over by its reader thread, and tells a real request
    // from the reader's end-of-stream signal by comparing against an empty String *by reference*.
    // Matching by value instead would read a blank line as the end of the stream: the worker would
    // stop before running anything and exit 0, and a forked group that ran nothing and exited
    // cleanly is a group sbt reports as passed.
    val w = WorkerExchange.startWorker(ForkOptions(), Nil, WorkerConnection.Tcp)
    w.println("")
    val exitCode = w.blockForExitCode()
    w.close()
    Result.assert(exitCode != 0).log(s"exitCode=$exitCode")

  def exForkNeverConnects: Result =
    // A bad JVM option kills the fork before it dials back.
    val fo = ForkOptions()
      .withRunJVMOptions(Vector("-XX:ThisOptionDoesNotExist"))
      .withConnectionTimeout(3.seconds)
    val startedAt = System.nanoTime()
    val outcome =
      try
        WorkerExchange.startWorker(fo, Nil, WorkerConnection.Tcp)
        None
      catch case NonFatal(e) => Some(e)
    val elapsed = (System.nanoTime() - startedAt).nanos
    Result
      .assert(outcome.isDefined)
      // Well inside the 30s default, so a build that asked to give up sooner really does.
      .and(Result.assert(elapsed < 20.seconds))
      .log(s"outcome=$outcome elapsed=$elapsed")

  /**
   * Calling the listeners with the registry lock held deadlocks sbt: [[React.notifyExit]] waits for
   * the thread that reads the worker's socket before judging a dead worker, and that reader delivers
   * through `notifyListeners`. The exit would hold the monitor the reader needs to make progress.
   */
  def exBroadcastOutsideLock: Result =
    val insideNotifyExit = CountDownLatch(1)
    val broadcast = CountDownLatch(1)
    val delivered = AtomicBoolean(false)
    val waiting = new WorkerResponseListener:
      def apply(line: String): Unit = ()
      def notifyExit(p: Process): Unit =
        insideNotifyExit.countDown()
        delivered.set(broadcast.await(10, TimeUnit.SECONDS))
        ()
    val reader = Thread(() => {
      insideNotifyExit.await(10, TimeUnit.SECONDS)
      WorkerExchange.notifyListeners("""{ "jsonrpc": "2.0" }""")
      broadcast.countDown()
    })
    try
      WorkerExchange.registerListener(waiting)
      reader.start()
      WorkerExchange.notifyExit(deadProcess)
      reader.join(30000L)
      Result
        .assert(delivered.get())
        .log(s"delivered=${delivered.get()}")
    finally WorkerExchange.unregisterListener(waiting)

  def exBoundExitGoesToOwner: Result =
    // blockForResponse waits on a promise only the exit completes, so an exit that misses its owner
    // hangs the build; one handed to every session instead lets a sibling judge a worker it knows
    // nothing about. Both listeners count only this worker's exit, since the watch thread of a
    // worker from an earlier test may still be on its way.
    val w = WorkerExchange.startWorker(ForkOptions(), Nil, WorkerConnection.Tcp)
    val owner = ExitListener(w.process)
    val bystander = ExitListener(w.process)
    try
      WorkerExchange.registerListener(bystander)
      w.bind(owner)
      w.println("""{"jsonrpc": "2.0", "method": "bye", "params": {}, "id": 1}""")
      val exitCode = w.blockForExitCode()
      owner.awaitExit()
      Result
        .assert(exitCode == 0)
        .and(Result.assert(owner.exits.get() == 1))
        .and(Result.assert(bystander.exits.get() == 0))
        .log(s"owner=${owner.exits.get()} bystander=${bystander.exits.get()}")
    finally
      WorkerExchange.unregisterListener(bystander)
      w.close()

  def exCloseFreesThePort: Result =
    // One listening socket per forked test group, and closing the proxy is the only thing that hands
    // it back. A build with many groups would otherwise run the sbt process out of descriptors.
    val w = WorkerExchange.startWorker(ForkOptions(), Nil, WorkerConnection.Tcp)
    val port = w.options.dropWhile(_ != "--tcp").drop(1).head.toInt
    w.println("""{"jsonrpc": "2.0", "method": "bye", "params": {}, "id": 1}""")
    w.blockForExitCode()
    w.close()
    val rebound =
      val probe = ServerSocket()
      // So a lingering connection cannot be mistaken for a socket still listening.
      probe.setReuseAddress(true)
      try
        probe.bind(InetSocketAddress(InetAddress.getByName(null), port), 1)
        true
      catch case NonFatal(_) => false
      finally probe.close()
    Result.assert(rebound).log(s"port=$port")

  private class ExitListener(target: Process) extends WorkerResponseListener:
    val exits: AtomicInteger = AtomicInteger(0)
    private val latch = CountDownLatch(1)
    def apply(line: String): Unit = ()
    def notifyExit(p: Process): Unit =
      if p eq target then
        exits.incrementAndGet()
        latch.countDown()
    def awaitExit(): Unit =
      latch.await(30, TimeUnit.SECONDS)
      ()

  private val deadProcess: Process = new Process:
    def isAlive(): Boolean = false
    def exitValue(): Int = 0
    def destroy(): Unit = ()

  /**
   * Broadcasts iterate a snapshot taken under the lock. Iterating the buffer itself throws
   * ConcurrentModificationException as another worker's session registers, which on the reader thread
   * of a healthy worker would abandon the rest of its notifications.
   */
  def exRegistryChurn: Result =
    val rounds = 2000
    val failure = AtomicReference[Throwable](null)
    def record(t: Throwable): Unit =
      failure.compareAndSet(null, t)
      ()
    val churn = Thread(() => {
      try
        for _ <- 0 until rounds do
          val l = ConcreteListener()
          WorkerExchange.registerListener(l)
          WorkerExchange.unregisterListener(l)
      catch case t: Throwable => record(t)
    })
    churn.start()
    try for i <- 0 until rounds do WorkerExchange.notifyListeners(s"""{"round": $i}""")
    catch case t: Throwable => record(t)
    churn.join(30000L)
    Result
      .assert(failure.get() == null)
      .log(s"failure=${failure.get()}")

  /**
   * Unbound, every line goes to every registered listener and each parses it to find out whether it
   * is theirs. A registered listener seeing nothing here is the point.
   */
  def propBind: Property =
    for
      i <- intGen.forAll
      w = WorkerExchange.startWorker(ForkOptions(), Nil, WorkerConnection.Tcp)
    yield
      val owner = ConcreteListener()
      w.bind(owner)
      withListener: bystander =>
        w.println(s"""{"jsonrpc": "2.0", "method": "bye", "params": {}, "id": $i}""")
        val exitCode = w.blockForExitCode()
        owner.awaitResponse()
        Result
          .assert(exitCode == 0)
          .and(
            Result.assert(owner.sb.toString() == s"""{ "jsonrpc": "2.0", "result": 0, "id": $i }""")
          )
          .and(Result.assert(bystander.sb.isEmpty))
          .log(s"owner=\"${owner.sb}\" bystander=\"${bystander.sb}\"")

  def propertyN(name: String, result: => Property, n: Int): Test =
    Test(name, result)
      .config(_.copy(testLimit = SuccessCount(n), shrinkLimit = ShrinkLimit(n * 10)))

  def propBadInput: Property =
    for
      ct <- gen[WorkerConnection].forAll
      w = WorkerExchange.startWorker(ForkOptions(), Nil, ct)
    yield
      w.println("{}")
      val exitCode = w.blockForExitCode()
      Result.assert(exitCode == 1)

  val intGen = Gen.int(Range.linear(1, 100))

  def propBye: Property =
    for
      ct <- gen[WorkerConnection].forAll
      i <- intGen.forAll
      w = WorkerExchange.startWorker(ForkOptions(), Nil, ct)
    yield withListener: l =>
      w.println(s"""{"jsonrpc": "2.0", "method": "bye", "params": {}, "id": $i}""")
      val exitCode = w.blockForExitCode()
      l.awaitResponse()
      Result
        .assert(exitCode == 0)
        .and(Result.assert(l.sb.toString() == s"""{ "jsonrpc": "2.0", "result": 0, "id": $i }"""))
        .log(s"\"${l.sb.toString()}\"")

  def propShape: Property =
    for i <- intGen.forAll
    yield
      val session = s"""{ "jsonrpc": "2.0", "result": 0, "id": $i }"""
      val errorResponse = s"""{ "jsonrpc": "2.0", "error": {"code": 1}, "id": $i }"""
      val notification =
        s"""{ "jsonrpc": "2.0", "method": "testLog", "params": {}, "re": $i }"""
      val request =
        s"""{ "jsonrpc": "2.0", "method": "nextTest", "params": {"id": $i, "framework": 0}, "id": 7 }"""
      def shape(line: String) = ForkTests.shapeOf(JsonParser.parseString(line).getAsJsonObject())
      Result
        .assert(shape(session) == ForkTests.Shape.Response)
        .and(Result.assert(shape(errorResponse) == ForkTests.Shape.Response))
        .and(Result.assert(shape(notification) == ForkTests.Shape.Notification))
        .and(Result.assert(shape(request) == ForkTests.Shape.Request))
        .and(Result.assert(shape("""{ "jsonrpc": "2.0" }""") == ForkTests.Shape.Unknown))

  def propDemux: Property =
    for
      idA <- intGen.forAll
      w = WorkerExchange.startWorker(ForkOptions(), Nil, WorkerConnection.Tcp)
    yield
      val idB = idA + 1
      val a = IdListener(idA)
      val b = IdListener(idB)
      try
        WorkerExchange.registerListener(a)
        WorkerExchange.registerListener(b)
        w.println(s"""{"jsonrpc": "2.0", "method": "bye", "params": {}, "id": $idA}""")
        val exitCode = w.blockForExitCode()
        a.awaitResponse()
        Result
          .assert(exitCode == 0)
          .and(Result.assert(a.accepted.size == 1))
          .and(Result.assert(b.accepted.isEmpty))
          .log(s"a=${a.accepted.toList} b=${b.accepted.toList}")
      finally
        WorkerExchange.unregisterListener(a)
        WorkerExchange.unregisterListener(b)

  class IdListener(id: Long) extends WorkerResponseListener:
    val accepted: ListBuffer[String] = ListBuffer.empty
    private val latch = CountDownLatch(1)
    def notifyExit(p: Process): Unit = ()
    def apply(line: String): Unit =
      val o = JsonParser.parseString(line).getAsJsonObject()
      val lineId =
        if o.has("id") then Some(o.getAsJsonPrimitive("id").getAsLong())
        else if o.has("re") then Some(o.getAsJsonPrimitive("re").getAsLong())
        else None
      if lineId.contains(id) then
        accepted += line
        latch.countDown()
    def awaitResponse(): Unit =
      latch.await(30, TimeUnit.SECONDS)
      ()

  def withListener[A1](f: ConcreteListener => A1) =
    val l = ConcreteListener()
    try
      WorkerExchange.registerListener(l)
      f(l)
    finally WorkerExchange.unregisterListener(l)

  class ConcreteListener extends WorkerResponseListener:
    import java.util.concurrent.{ CountDownLatch, TimeUnit }
    val sb = StringBuilder()
    private val latch = CountDownLatch(1)
    def notifyExit(p: Process): Unit = ()
    def apply(line: String): Unit =
      sb.append(line)
      latch.countDown()
    def awaitResponse(): Unit = latch.await(30, TimeUnit.SECONDS)
end WorkerExchangeTest
