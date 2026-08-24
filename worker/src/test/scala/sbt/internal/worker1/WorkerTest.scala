package sbt.internal.worker1

import org.scalasbt.shadedgson.com.google.gson.{ JsonObject, JsonParser }
import java.io.{
  ByteArrayInputStream,
  ByteArrayOutputStream,
  PipedInputStream,
  PipedOutputStream,
  PrintStream
}
import sbt.testing.*
import java.net.{ InetAddress, ServerSocket }
import java.util.{ ArrayList, List as JList, Scanner }
import java.util.concurrent.{ Callable, ConcurrentLinkedQueue, CountDownLatch, Executors, TimeUnit }
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

/**
 * Every request a test here feeds `WorkerMain` must be well-formed JSON-RPC.
 *
 * This project's tests run unforked -- see `workerProj` in build.sbt for why -- so they share the sbt
 * server's JVM, and `WorkerMain.process` calls `System.exit(1)` on a request with no "jsonrpc" field.
 * Forked that ends one worker; here it takes the whole build down, with no diagnostic to say which
 * test did it.
 */
object WorkerTest extends verify.BasicTestSuite:

  test("the reader reports the end of the stream even when no request ever arrives") {
    // socketWork blocks taking the one request the reader hands over, and when sbt goes away
    // before sending anything only the end-of-stream signal stops the JVM waiting there forever.
    val out = PrintStream(ByteArrayOutputStream())
    val in = Scanner(ByteArrayInputStream(Array.empty[Byte]), "UTF-8")
    val rpc = WorkerRpc(out, in, 1000L)
    val ended = CountDownLatch(1)
    val requests = AtomicInteger(0)
    rpc.start(_ => { requests.incrementAndGet(); () }, () => ended.countDown())
    // A generous bound, not a timing assertion: this can only fail if the signal never fires.
    assert(ended.await(30, TimeUnit.SECONDS))
    assert(requests.get() == 0)
    // Running out of lines before close() means sbt went away, which is not a drained queue.
    assert(rpc.isBroken())
    rpc.close()
  }

  /**
   * Runs `body` against a WorkerRpc whose sbt answers each request id with `reply(id)`; a None
   * leaves that request unanswered.
   */
  private def withFakeSbt[A](timeoutMillis: Long, onRequest: String => Unit = _ => ())(
      reply: Long => Option[String]
  )(
      body: WorkerRpc => A
  ): A =
    val sbtIn = PipedInputStream(64 * 1024)
    val workerOut = PrintStream(PipedOutputStream(sbtIn), true, "UTF-8")
    val workerIn = PipedInputStream(64 * 1024)
    val sbtOut = PrintStream(PipedOutputStream(workerIn), true, "UTF-8")
    val rpc = WorkerRpc(workerOut, Scanner(workerIn, "UTF-8"), timeoutMillis)
    rpc.start(onRequest(_), () => ())
    val fakeSbt = Thread(() => {
      // Ends by throwing when the pipe closes, which is how this thread is meant to stop.
      try
        val scanner = Scanner(sbtIn, "UTF-8")
        while scanner.hasNextLine() do
          val o = JsonParser.parseString(scanner.nextLine()).getAsJsonObject()
          reply(o.getAsJsonPrimitive("id").getAsLong()).foreach(sbtOut.println)
      catch case _: Throwable => ()
    })
    fakeSbt.setDaemon(true)
    fakeSbt.start()
    try body(rpc)
    finally
      rpc.close()
      workerOut.close()
      sbtOut.close()

  test("a null result means no more work, which is not a broken channel") {
    // The distinction the whole run rests on: a drained queue must let the worker finish and exit 0,
    // while a channel failure must be fatal. Read the wrong way round, either every healthy run
    // fails or a worker silently abandons the classes sbt handed it.
    withFakeSbt(30000L)(id => Some(s"""{ "jsonrpc": "2.0", "result": null, "id": $id }""")): rpc =>
      val got = rpc.requestIndex("nextTest", """{"id": 1, "framework": 0, "done": null}""")
      assert(got == null)
      assert(!rpc.isBroken())
  }

  test("an error reply is a broken channel, not a drained queue") {
    withFakeSbt(30000L)(id => Some(s"""{ "jsonrpc": "2.0", "error": {"code": 1}, "id": $id }""")):
      rpc =>
        assert(rpc.requestIndex("nextTest", """{"id": 1, "framework": 0}""") == null)
        assert(rpc.isBroken())
  }

  test("a request sbt never answers times out as a broken channel") {
    // Short on purpose: the assertion is that the wait ends at all and is not read as "no work".
    withFakeSbt(500L)(_ => None): rpc =>
      assert(rpc.requestIndex("nextTest", """{"id": 1, "framework": 0}""") == null)
      assert(rpc.isBroken())
  }

  test("a request after close is refused rather than left waiting") {
    withFakeSbt(30000L)(id => Some(s"""{ "jsonrpc": "2.0", "result": 0, "id": $id }""")): rpc =>
      rpc.close()
      assert(rpc.requestIndex("nextTest", """{"id": 1, "framework": 0}""") == null)
  }

  test("a request after the channel broke is refused rather than left waiting") {
    // The shape this takes in a real run: sbt goes away while a stealer is part way through a class,
    // and the request that stealer makes when the class finishes arrives after the reader thread has
    // already failed every future it found waiting. Nothing is left to complete a future registered
    // after that, so the caller would sit out the reply timeout -- ten minutes -- and runStealing
    // joins every stealer before it fails, which keeps the whole worker JVM alive that long after
    // sbt is gone.
    val out = PrintStream(ByteArrayOutputStream())
    val in = Scanner(ByteArrayInputStream(Array.empty[Byte]), "UTF-8")
    val rpc = WorkerRpc(out, in, 600000L)
    val ended = CountDownLatch(1)
    rpc.start(_ => (), () => ended.countDown())
    assert(ended.await(30, TimeUnit.SECONDS), "the reader never reported the end of the stream")
    assert(rpc.isBroken())

    val returned = CountDownLatch(1)
    val caller = Thread(() => {
      val _ = rpc.requestIndex("nextTest", """{"id": 1, "framework": 0}""")
      returned.countDown()
    })
    caller.setDaemon(true)
    caller.start()
    // A generous bound, not a timing assertion: the wait it replaces is the ten minute timeout above.
    assert(returned.await(30, TimeUnit.SECONDS), "a request made after EOF waited for the timeout")
    rpc.close()
  }

  test("closing the channel releases a request already waiting on it") {
    // The other half of that contract: a caller registered before the close must be let go, not left
    // on the reply timeout, which is ten minutes. Nothing closes mid-run today, so this pins the
    // release for whatever cancellation path is added next.
    val asked = CountDownLatch(1)
    // Never answered, so the only way out is the close below.
    withFakeSbt(600000L)(_ => { asked.countDown(); None }): rpc =>
      val returned = CountDownLatch(1)
      val caller = Thread(() => {
        val _ = rpc.requestIndex("nextTest", """{"id": 1, "framework": 0}""")
        returned.countDown()
      })
      caller.setDaemon(true)
      caller.start()
      // sbt having read the request means it was registered first: requestIndex puts the future in
      // `pending` before writing. Without this the close could win the race and take the cheap
      // `closed` path instead, which is the case above.
      assert(asked.await(30, TimeUnit.SECONDS), "the request was never written")
      rpc.close()
      assert(returned.await(30, TimeUnit.SECONDS), "close left the caller on the reply timeout")
  }

  test("a line carrying a method is never taken as a reply") {
    // sbt's requests and its replies share one channel. Matching a request as a reply would both
    // complete a waiting stealer with a payload meant for nobody and lose the request, so the shape
    // decides this, not whether the id happens to be outstanding.
    val served = ConcurrentLinkedQueue[String]()
    // Well-formed but for the `method`, and carrying this request's own id: only the method tells it
    // apart from the reply the caller is waiting for.
    withFakeSbt(1500L, s => { served.add(s); () })(id =>
      Some(s"""{ "jsonrpc": "2.0", "method": "ping", "params": {}, "result": 7, "id": $id }""")
    ): rpc =>
      val got = rpc.requestIndex("nextTest", """{"id": 1, "framework": 0}""")
      assert(got == null, s"a request completed a pending reply with $got")
      val lines = served.toArray(Array.empty[String]).toVector
      assert(lines.size == 1 && lines.head.contains("\"method\""), s"served=$lines")
  }

  test("a line carrying neither a result nor an error is never taken as a reply") {
    // The other half of the reply shape, the `method` case above being the first. A line with a
    // matching id but no payload would complete the waiting stealer with nothing, and a null that is
    // not marked broken means "the queue is drained" -- so the worker would stop leasing and exit 0.
    // sbt's undrained-queue check is the backstop for that, so what this guard buys is the run
    // failing where the two sides disagreed rather than one layer out.
    val served = ConcurrentLinkedQueue[String]()
    withFakeSbt(1500L, s => { served.add(s); () })(id =>
      Some(s"""{ "jsonrpc": "2.0", "id": $id }""")
    ): rpc =>
      val got = rpc.requestIndex("nextTest", """{"id": 1, "framework": 0}""")
      assert(got == null, s"a payloadless line completed a pending reply with $got")
      assert(rpc.isBroken(), "a request nothing answered must not read as a drained queue")
      val lines = served.toArray(Array.empty[String]).toVector
      assert(lines.size == 1 && !lines.head.contains("result"), s"served=$lines")
  }

  test("concurrent requests each get their own reply, whatever order sbt answers in") {
    // The stealer threads of one worker share a WorkerRpc, so a reply matched by arrival rather
    // than by id would hand a thread the class index meant for another: one class run twice and
    // another never run, which the queue's own accounting cannot see.
    val callers = 8
    // Requests the worker writes and the fake sbt below reads.
    val sbtIn = PipedInputStream(64 * 1024)
    val workerOut = PrintStream(PipedOutputStream(sbtIn), true, "UTF-8")
    // Replies the fake sbt writes and the rpc reader thread reads.
    val workerIn = PipedInputStream(64 * 1024)
    val sbtOut = PrintStream(PipedOutputStream(workerIn), true, "UTF-8")
    val rpc = WorkerRpc(workerOut, Scanner(workerIn, "UTF-8"), 30000L)
    rpc.start(_ => (), () => ())

    // Holds every request until all of them have arrived, then answers in reverse, so the replies
    // come back in the opposite order to the asking. Each caller asked for its own number back.
    val fakeSbt = Thread(() => {
      val scanner = Scanner(sbtIn, "UTF-8")
      val pending = ListBuffer.empty[(Long, Int)]
      while pending.size < callers && scanner.hasNextLine() do
        val o = JsonParser.parseString(scanner.nextLine()).getAsJsonObject()
        pending += ((
          o.getAsJsonPrimitive("id").getAsLong(),
          o.getAsJsonObject("params").getAsJsonPrimitive("caller").getAsInt()
        ))
      pending.reverse.foreach: (id, caller) =>
        sbtOut.println(s"""{ "jsonrpc": "2.0", "result": $caller, "id": $id }""")
    })
    fakeSbt.setDaemon(true)
    fakeSbt.start()

    // One thread per caller, so every request really is in flight together: the fake sbt answers
    // nothing until it holds all of them.
    val pool = Executors.newFixedThreadPool(callers)
    try
      val jobs = (0 until callers).map: c =>
        new Callable[(Int, Integer)]:
          def call(): (Int, Integer) = (c, rpc.requestIndex("nextTest", s"""{"caller": $c}"""))
      val got = pool.invokeAll(jobs.asJava).asScala.toVector.map(_.get)
      assert(got.size == callers)
      assert(
        got.forall((c, reply) => reply != null && reply.intValue() == c),
        s"crossed replies: $got"
      )
    finally
      pool.shutdown()
      pool.awaitTermination(30, TimeUnit.SECONDS)
      rpc.close()
      fakeSbt.join(30000L)
      workerOut.close()
      sbtOut.close()
  }

  /** A TestInfo asking for `names` in queue mode, as ForkTests builds it for a spread group. */
  private def queueModeInfo(
      names: Seq[String],
      threads: Integer,
      frameworkClass: String = "sbt.internal.worker1.StealingFramework",
      queueMode: Boolean = true
  ): TestInfo =
    val defs = ArrayList[TaskDef]()
    // Wrapped as ForkTests wraps it, so this TestInfo is one gson can put on the wire.
    val print = ForkTestMain.SubclassFingerscan(StealingFramework.print)
    names.foreach(n => defs.add(TaskDef(n, print, false, Array(SuiteSelector()))))
    val runners = ArrayList[TestInfo.TestRunner]()
    runners.add(
      TestInfo.TestRunner(
        ArrayList(JList.of(frameworkClass)),
        ArrayList[String](),
        ArrayList[String]()
      )
    )
    TestInfo(
      true,
      RunInfo.JvmRunInfo(ArrayList[String](), ArrayList[FilePath](), "", false),
      null,
      false,
      true,
      threads,
      defs,
      runners,
      queueMode
    )

  /**
   * Drives the worker's queue mode against sbt's side of the protocol: `hand` decides what to answer
   * each nextTest request. Returns the requests the worker sent and the notifications it wrote, the
   * latter being how a failure reaches sbt — ForkTestMain reports rather than throws.
   */
  private def runStealing(
      names: Seq[String],
      threads: Integer,
      failOn: Option[String] = None,
      failTasksOn: Option[String] = None,
      failTaskDefOn: Option[String] = None,
      nestedOf: Map[String, String] = Map.empty
  )(hand: Int => String): (Vector[String], String) =
    StealingFramework.reset()
    StealingFramework.failOn = failOn
    StealingFramework.failTasksOn = failTasksOn
    StealingFramework.failTaskDefOn = failTaskDefOn
    StealingFramework.nestedOf = nestedOf
    val sbtIn = PipedInputStream(64 * 1024)
    val workerOut = PrintStream(PipedOutputStream(sbtIn), true, "UTF-8")
    val workerIn = PipedInputStream(64 * 1024)
    val sbtOut = PrintStream(PipedOutputStream(workerIn), true, "UTF-8")
    val rpc = WorkerRpc(workerOut, Scanner(workerIn, "UTF-8"), 30000L)
    rpc.start(_ => (), () => ())
    val requests = ConcurrentLinkedQueue[String]()
    val served = AtomicInteger(0)
    val fakeSbt = Thread(() => {
      try
        val scanner = Scanner(sbtIn, "UTF-8")
        while scanner.hasNextLine() do
          val line = scanner.nextLine()
          val o = JsonParser.parseString(line).getAsJsonObject()
          if o.has("method") && o.getAsJsonPrimitive("method").getAsString() == "nextTest" then
            requests.add(o.getAsJsonObject("params").toString)
            val id = o.getAsJsonPrimitive("id").getAsLong()
            val next = hand(served.getAndIncrement())
            sbtOut.println(s"""{ "jsonrpc": "2.0", "result": $next, "id": $id }""")
      catch case _: Throwable => ()
    })
    fakeSbt.setDaemon(true)
    fakeSbt.start()
    val notifications = ByteArrayOutputStream()
    ForkTestMain.main(
      1L,
      queueModeInfo(names, threads),
      PrintStream(notifications, true, "UTF-8"),
      getClass.getClassLoader,
      rpc
    )
    rpc.close()
    workerOut.close()
    sbtOut.close()
    (requests.toArray(Array.empty[String]).toVector, notifications.toString("UTF-8"))

  test("a worker in queue mode runs the classes it leases and acknowledges each one") {
    // The whole point of the mode: sbt hands out indices into the full taskDefs vector, the worker
    // runs exactly those, and every one comes back acknowledged so sbt can tell run from lost.
    val (requests, notes) =
      runStealing(Seq("A", "B", "C", "D"), 1): i =>
        // Lease index 2, then 0, then say there is no more work.
        if i == 0 then "2" else if i == 1 then "0" else "null"
    assert(StealingFramework.executed.toArray.toVector == Vector("C", "A"))
    assert(!notes.contains("forkError"), notes)
    // Three requests: the opening one, one acknowledging C, one acknowledging A.
    assert(requests.size == 3, s"requests=$requests")
    assert(requests(0).contains("\"done\":null"), requests(0))
    assert(requests(1).contains("\"done\":2"), requests(1))
    assert(requests(2).contains("\"done\":0"), requests(2))
    // Each class it ran is reported, so sbt records a result rather than an absence.
    assert(notes.contains("startTestGroup") && notes.contains("endTestGroup"))
  }

  test("the runner is told the run is done, once, after its classes have finished") {
    // done() is where a framework flushes what it accumulated over the whole run — ScalaTest prints
    // its summary there. Skipping it loses that with the worker still exiting 0, and calling it twice
    // would print the summary twice, since the shutdown hook is armed for the crash case.
    val (_, _) = runStealing(Seq("A", "B"), 1): i =>
      if i < 2 then i.toString else "null"
    assert(StealingFramework.executed.size == 2)
    val calls = StealingFramework.doneCalls.get()
    assert(calls == 1, s"done() was called $calls times")
  }

  test("the nested tasks a class hands back are run and reported too") {
    // ScalaTest and specs2 return a nested suite as a task for the runner to execute rather than
    // running it themselves. Not walking into them would run the outer class, report it as a pass,
    // and never touch a single test inside — with nothing anywhere saying so.
    val (_, notes) =
      runStealing(Seq("A"), 1, nestedOf = Map("A" -> "A.Inner")): i =>
        if i == 0 then "0" else "null"
    val ran = StealingFramework.executed.toArray(Array.empty[String]).toVector
    assert(ran == Vector("A", "A.Inner"), s"ran=$ran")
    // Reported as a suite of its own, so sbt records a result rather than an absence.
    assert(notes.contains("A.Inner"), notes)
  }

  test("a test task that fails outside the run's own guard fails the worker") {
    // runTest guards `execute`, so a test that throws becomes a reported error. What is not guarded
    // is asking the task what it is, and writing the group's start and end -- a failure there fails
    // the task's future. Joined and swallowed, that class ended with no report at all while its
    // lease was acknowledged like any other, so sbt saw a run that owed nothing and read the whole
    // group as a pass.
    val (requests, notes) =
      runStealing(Seq("A", "B"), 1, failTaskDefOn = Some("B")): i =>
        if i < 2 then i.toString else "null"
    assert(notes.contains("forkError"), notes)
    // A ran and was acknowledged; B never reports, and its lease is not acknowledged either, so
    // sbt's own guard names it as well.
    assert(StealingFramework.executed.toArray.toVector == Vector("A"))
    assert(!requests.exists(_.contains("\"done\":1")), s"requests=$requests")
  }

  test("a worker in queue mode refuses an index that is not one of its classes") {
    // sbt and the worker index the same vector. If that ever drifts, running whatever sits at the
    // index would report the wrong class, so the worker must reject it instead of guessing.
    val (_, notes) = runStealing(Seq("A", "B"), 1)(_ => "9")
    assert(StealingFramework.executed.isEmpty)
    assert(notes.contains("forkError"), notes)
  }

  test("a worker with no parallelism asked for runs one class at a time") {
    // In queue mode the JVM count is the parallelism dial, not availableProcessors.
    val (_, _) = runStealing(Seq("A", "B", "C", "D"), null): i =>
      if i < 4 then i.toString else "null"
    assert(StealingFramework.executed.size == 4)
    assert(StealingFramework.peak.get() == 1, s"peak=${StealingFramework.peak.get()}")
  }

  test("a worker asked for two threads runs two classes at a time") {
    val (_, _) = runStealing(Seq("A", "B", "C", "D"), 2): i =>
      if i < 4 then i.toString else "null"
    assert(StealingFramework.executed.size == 4)
    assert(StealingFramework.peak.get() == 2, s"peak=${StealingFramework.peak.get()}")
  }

  test("a stealer thread that fails reports rather than finishing quietly") {
    // The class it held is leased on sbt's side and unacknowledged, so a silent finish would drop it
    // from the report and the group would pass with a class missing. An exception inside a test does
    // not reach here — the task wrapper turns that into an error event — so this needs the framework
    // itself to fail on the class.
    val (_, notes) =
      runStealing(Seq("A", "B"), 1, failTasksOn = Some("B")): i =>
        if i == 0 then "1" else "null"
    assert(notes.contains("A forked test worker thread failed"), notes)
  }

  test("a failing stealer does not cut short the suites its siblings are running") {
    // Every stealer is joined before the failure is reported. Reporting first would let sbt fail the
    // group and the JVM exit while another thread was still running a class, leaving that class's
    // suite half-reported. The failing class is the first one handed out, so the thread that dies is
    // the one whose result is collected first — the case where abandoning the rest is observable.
    val (_, notes) =
      runStealing(Seq("A", "B", "C", "D"), 2, failTasksOn = Some("A")): i =>
        if i < 4 then i.toString else "null"
    assert(notes.contains("A forked test worker thread failed"), notes)
    val ran = StealingFramework.executed.toArray(Array.empty[String]).toVector.sorted
    assert(ran == Vector("B", "C", "D"), s"ran=$ran\n$notes")
  }

  test("queue mode with no channel to ask on fails instead of running nothing") {
    // Over a transport with no reply channel there is nobody to lease from. Returning quietly would
    // run none of the group's classes and still exit 0.
    StealingFramework.reset()
    val notifications = ByteArrayOutputStream()
    ForkTestMain.main(
      1L,
      queueModeInfo(Seq("A"), 1),
      PrintStream(notifications, true, "UTF-8"),
      getClass.getClassLoader
    )
    val notes = notifications.toString("UTF-8")
    assert(StealingFramework.executed.isEmpty)
    assert(notes.contains("forkError"), notes)
  }

  test("the notifications a worker writes are readable by the side that consumes them") {
    // sbt parses these with this same gson and hands the events to SuiteResult, which dereferences
    // every event's throwable and reads its fingerprint and selector back. An adapter that writes a
    // shape its own reader cannot take back does not lose one field: it loses the whole suite.
    val (_, notes) =
      runStealing(Seq("A"), 1, failOn = Some("A")): i =>
        if i == 0 then "0" else "null"
    val g = WorkerMain.mkGson()
    val byMethod = notes.linesIterator
      .map(l => JsonParser.parseString(l).getAsJsonObject())
      .filter(_.has("method"))
      .toVector
      .groupBy(_.getAsJsonPrimitive("method").getAsString())
    def params(method: String): Vector[JsonObject] =
      byMethod.getOrElse(method, Vector.empty).map(_.getAsJsonObject("params"))

    val start = params("startTestGroup").map(g.fromJson(_, classOf[ForkTestMain.ForkGroupStart]))
    val end = params("endTestGroup").map(g.fromJson(_, classOf[ForkTestMain.ForkGroupEnd]))
    assert(start.map(_.group) == Vector("A"), notes)
    assert(end.map(_.group) == Vector("A"), notes)
    assert(start.map(_.id) == Vector(1L) && end.map(_.id) == Vector(1L), notes)

    val progress = params("testProgress").map(g.fromJson(_, classOf[ForkTestMain.ForkEventsInfo]))
    assert(progress.size == 1, notes)
    val event = progress.head.events.get(0)
    assert(progress.head.group == "A", notes)
    // Compared outside the assertion: verify's macro rewrites a Java enum constant into a reference
    // that does not exist at runtime.
    val reportedAsError = event.status() == Status.Error
    assert(reportedAsError, notes)
    // The unset-throwable case is what SuiteResult cannot survive, so both halves are asserted.
    assert(event.throwable() != null && event.throwable().isDefined(), notes)
    assert(event.throwable().get().getMessage().contains("A was told to fail"), notes)
    // What the XML report and the failure recap print, so an asymmetric field name loses it.
    assert(event.throwable().get().getStackTrace().nonEmpty, notes)
    assert(event.fingerprint().isInstanceOf[SubclassFingerprint], notes)
    assert(event.selector().isInstanceOf[SuiteSelector], notes)

    val errors = params("forkError").map(g.fromJson(_, classOf[ForkTestMain.ForkErrorInfo]))
    assert(errors.nonEmpty && errors.forall(_.error != null), notes)
  }

  /**
   * Plays sbt to a real `socketWork`: accepts its connection, answers `nextTest` and returns the
   * lines it wrote. The read timeout is what keeps a worker that stops answering from hanging this
   * suite rather than failing it.
   */
  private def withSocketWorker(
      request: TestInfo => String
  )(hand: Int => String): (Vector[String], Boolean) =
    val server = ServerSocket(0, 1, InetAddress.getByName(null))
    val worker = Thread(() => WorkerMain().socketWork(server.getLocalPort()))
    worker.setDaemon(true)
    worker.start()
    val socket = server.accept()
    socket.setSoTimeout(30000)
    val lines = ListBuffer.empty[String]
    try
      val toWorker = PrintStream(socket.getOutputStream(), true, "UTF-8")
      val fromWorker = Scanner(socket.getInputStream(), "UTF-8")
      toWorker.println(request(queueModeInfo(Seq("A", "B"), 1)))
      var served = 0
      var responded = false
      try
        while !responded && fromWorker.hasNextLine() do
          val line = fromWorker.nextLine()
          lines += line
          val o = JsonParser.parseString(line).getAsJsonObject()
          if o.has("method") then
            if o.getAsJsonPrimitive("method").getAsString() == "nextTest" then
              val id = o.getAsJsonPrimitive("id").getAsLong()
              toWorker.println(s"""{ "jsonrpc": "2.0", "result": ${hand(served)}, "id": $id }""")
              served += 1
          else if o.has("id") then responded = true
      catch case _: Throwable => ()
      worker.join(30000L)
      (lines.toVector, responded && !worker.isAlive())
    finally
      socket.close()
      server.close()

  test("the worker answers a request without occupying the thread that reads sbt's replies") {
    // In queue mode the run blocks on nextTest replies, and only the reader thread can deliver them.
    // Serving the request on that thread deadlocks the worker: it would sit waiting for an answer it
    // is itself keeping from being read, until the RPC timeout kills the whole test run.
    StealingFramework.reset()
    val g = WorkerMain.mkGson()
    val (lines, finished) =
      withSocketWorker(info =>
        s"""{ "jsonrpc": "2.0", "method": "test", "params": ${g
            .toJson(info, classOf[TestInfo])}, "id": 1 }"""
      )(i => if i < 2 then i.toString else "null")
    assert(finished, lines.mkString("\n"))
    val ran = StealingFramework.executed.toArray(Array.empty[String]).toVector.sorted
    assert(ran == Vector("A", "B"), lines.mkString("\n"))
    assert(lines.last.contains("\"result\": 0"), lines.last)
  }

  test("a worker whose sbt goes away without asking anything stops instead of waiting") {
    // socketWork blocks on the one request the reader hands over. Without the end-of-stream signal
    // the JVM would sit there for ever, holding a slot in the build's forked-JVM budget.
    val server = ServerSocket(0, 1, InetAddress.getByName(null))
    val worker = Thread(() => WorkerMain().socketWork(server.getLocalPort()))
    worker.setDaemon(true)
    worker.start()
    val socket = server.accept()
    socket.close()
    server.close()
    worker.join(30000L)
    assert(!worker.isAlive())
  }

  test("a framework the fork cannot load fails the run instead of skipping its classes") {
    // sbt only sends frameworks it loaded from this same test classpath, so one missing here means
    // the fork's classpath is not what sbt discovered against. Skipping ran none of that
    // framework's classes and still exited 0, which reads as a group that passed.
    StealingFramework.reset()
    val notifications = ByteArrayOutputStream()
    ForkTestMain.main(
      1L,
      queueModeInfo(Seq("A"), 1, frameworkClass = "does.not.Exist", queueMode = false),
      PrintStream(notifications, true, "UTF-8"),
      getClass.getClassLoader
    )
    val notes = notifications.toString("UTF-8")
    assert(StealingFramework.executed.isEmpty)
    assert(notes.contains("forkError"), notes)
    assert(notes.contains("Could not load test framework"), notes)
  }

  test("a run releases the threads it created") {
    // The worker's own JVM exits when its run is over, which hides a pool left running. sbt's does
    // not: this project's tests drive ForkTestMain in-process, and a leaked pool would add a thread
    // to the build server for every run.
    def poolThreads: Int =
      Thread.getAllStackTraces.keySet.asScala.count(t => t.getName.startsWith("pool-") && t.isAlive)
    val before = poolThreads
    runStealing(Seq("A", "B"), 2)(i => if i < 2 then i.toString else "null")
    val deadline = System.currentTimeMillis() + 30000L
    while poolThreads > before && System.currentTimeMillis() < deadline do Thread.sleep(50)
    assert(poolThreads <= before, s"before=$before after=$poolThreads")
  }

  test("a run request is answered once, and a run that failed is not answered with a result") {
    // The reply goes to `jsonOut`, which the constructor captures from `System.out` -- so redirecting
    // stdout across the construction is what makes it readable. Without that there is nothing to
    // assert on, and this test could not fail whatever `process` did.
    //
    // The main class is deliberately one that is not there. These tests run unforked, so `run`
    // delegates parent-first to the system classloader, which is then sbt's own: a Scala main class
    // would resolve `scala.Predef$` from sbt's library rather than from the classpath the request
    // carries, and could not run whatever the request said. A class that is absent fails the same way
    // on any classloader, and the outcome worth pinning is the protocol one -- the failure comes back
    // as an error against the request's id rather than as a result, which is the only thing telling
    // sbt a run that worked from one that did not. The stack trace this prints is the worker
    // reporting that failure, not this test failing.
    val captured = ByteArrayOutputStream()
    val saved = System.out
    val worker = {
      System.setOut(PrintStream(captured, true, "UTF-8"))
      try WorkerMain()
      finally System.setOut(saved)
    }
    val runInfo =
      """{ "jvm": true, "jvmRunInfo":
        |{ "args": ["hi"], "classpath": [], "mainClass": "example.NoSuchMainClass" } }""".stripMargin
    worker.process(s"""{ "jsonrpc": "2.0", "id": 1, "method": "run", "params": $runInfo }""")
    val replies = captured.toString("UTF-8").linesIterator.filter(_.contains("jsonrpc")).toList
    assert(replies.size == 1, s"expected exactly one reply, got $replies")
    val o = JsonParser.parseString(replies.head).getAsJsonObject()
    assert(
      o.getAsJsonPrimitive("id").getAsLong() == 1L,
      s"the reply carried the wrong id: $replies"
    )
    assert(o.has("error") && !o.has("result"), s"a failed run replied with a result: $replies")
  }
end WorkerTest
