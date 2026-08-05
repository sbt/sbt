package sbt
package internal

import hedgehog.*
import hedgehog.runner.*
import hedgehog.core.Result
// `_root_` because `import hedgehog.*` brings a `hedgehog.sbt` into scope, which would otherwise
// shadow this package.
import _root_.sbt.util.{ Level, Logger }
import _root_.sbt.protocol.testing.TestResult
import org.scalasbt.shadedgson.com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.util.concurrent.{ Callable, CountDownLatch, Executors }
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger, AtomicReference }
import scala.collection.mutable
import scala.concurrent.{ Future, Promise }
import scala.sys.process.Process

/**
 * Pins how [[React]] accounts for the test classes a worker leases.
 *
 * `TestQueue.remaining` only knows whether a unit was handed out, so a class the worker takes and
 * then never runs is invisible to it, and with several workers the others report normally and the
 * missing class reads as a pass. Asserted here rather than in a scripted test, which cannot make a
 * framework fail to load only inside the fork.
 */
object ReactTest extends Properties:

  override lazy val tests: List[Test] = List(
    example("a class leased and never acknowledged is reported", exLeaseWithoutAck),
    example("a lease this session cannot name is still reported", exLeaseWithoutAName),
    example("acknowledging every leased class leaves nothing owed", exFullRun),
    example("a class that produced no test task still counts as run", exNoTasksProduced),
    example("the two leases of a class matching two frameworks are tracked apart", exTwoFrameworks),
    example("a request carrying another session's id leases nothing", exForeignSession),
    example("a clean exit with a suite still open fails the run", exCleanExitMidSuite),
    example("a clean exit with every suite finished passes", exCleanExitAfterSuites),
    example("an exit is not judged until the notifications have arrived", exWaitsForTheStream),
    example("an exit reaching two threads at once is judged once", exExitJudgedOnce),
    example("another worker's exit is not this session's to judge", exForeignExit),
    example(
      "requests arriving at once lease each class to exactly one of them",
      exConcurrentRequests
    ),
    example("a listener that throws does not hide the crash from the others", exListenerThrows),
    example("an error response to the test request fails the run", exErrorResponse),
    example("a late error response cannot turn a finished run red", exFirstCompletionWins),
    example("a worker that exits non-zero fails the run", exNonZeroExit),
    example("a dying worker stops the queue serving the others", exDeathPoisonsTheQueue),
    example("another session's notifications are not recorded here", exForeignNotification),
    example("another session's response cannot finish this run", exForeignResponse),
    example("a worker's own output reaches the log at the level it asked for", exLogLevels),
    example("the events of a suite become its result", exProgressBecomesResult),
    example("a class run under two frameworks reports both runs", exOneClassTwoFrameworks),
    example("a suite's events are taken when it ends, not copied", exEventsConsumedOnEnd),
    example("a request sbt does not serve is reported, not ignored", exUnservedRequest),
    example("a suite sbt cannot record is reported, not dropped", exUnrecordableSuite),
    example("a result sbt cannot attribute is reported, not dropped", exUnattributableResult),
    example("a notification sbt has no case for is reported, not dropped", exUnhandledMethod),
    example("ordinary worker output is logged, not treated as a lost result", exPlainOutput),
    example("a line of no recognised shape neither records nor ends the run", exUnknownShape),
  )

  private val id = 42L
  private val names = Vector("A", "B", "C")

  private def reply(result: String, reqId: Int): String =
    s"""{ "jsonrpc": "2.0", "result": $result, "id": $reqId }"""

  private class Fixture(
      queue: Option[TestQueue],
      listeners: Seq[TestReportListener] = Nil,
      streamEnd: Future[Unit] = Future.unit,
      exitCode: Int = 0,
      log: Logger = Logger.Null
  ):
    private val out = ByteArrayOutputStream()

    // isAlive is false so WorkerProxy's watch thread finishes at once rather than outliving the
    // test JVM. The one notifyExit it broadcasts is harmless: this React is never registered.
    private val process: Process = new Process:
      def isAlive(): Boolean = false
      def exitValue(): Int = exitCode
      def destroy(): Unit = ()

    val results: mutable.Map[String, SuiteResult] = mutable.Map.empty
    val proxy: WorkerProxy =
      WorkerProxy(out, process, Nil, None, streamEnd, AtomicReference[WorkerResponseListener](null))
    val react: React =
      React(
        id,
        log,
        listeners,
        results,
        proxy,
        queue,
        names
      )

    /** The exit notification the process watch thread would deliver. */
    def notifyExit(): Unit = react.notifyExit(process)

    /** An exit broadcast for some other worker, which reaches this listener too. */
    def notifyForeignExit(): Unit = react.notifyExit(Fixture.deadStranger)

    /** One steal request: `done` is the index the requesting thread has just finished, if any. */
    def nextTest(reqId: Int, done: String = "null", session: Long = id, framework: Int = 0): Unit =
      val params = s"""{"id": $session, "framework": $framework, "done": $done}"""
      react(s"""{ "jsonrpc": "2.0", "method": "nextTest", "params": $params, "id": $reqId }""")

    def startGroup(group: String): Unit = send("startTestGroup", group)
    def endGroup(group: String): Unit = send("endTestGroup", group)

    /** An event whose throwable the worker never set, which SuiteResult cannot read. */
    def brokenProgress(group: String): Unit =
      val event = s"""{"fullyQualifiedName": "$group",
                     |"selector": {"type": "SuiteSelector"}, "status": "Success"}""".stripMargin
      val params = s"""{"id": $id, "group": "$group", "events": [$event]}"""
      react(s"""{ "jsonrpc": "2.0", "method": "testProgress", "params": $params, "re": $id }""")

    /** One test event for `group`, as the worker reports it while the suite runs. */
    def testProgress(group: String, status: String): Unit =
      val event =
        s"""{"fullyQualifiedName": "$group",
            |"fingerprint": {"type": "SubclassFingerscan", "isModule": false,
            |  "superclassName": "junit.framework.TestCase", "requireNoArgConstructor": false},
            |"selector": {"type": "SuiteSelector"},
            |"status": "$status", "throwable": {}, "duration": 1}""".stripMargin
      val params = s"""{"id": $id, "group": "$group", "events": [$event]}"""
      react(s"""{ "jsonrpc": "2.0", "method": "testProgress", "params": $params, "re": $id }""")

    /** One log line, as the worker forwards everything its test loggers are given. */
    def testLog(tag: String, message: String, session: Long = id, re: Long = id): Unit =
      val params = s"""{"id": $session, "tag": "$tag", "message": "$message"}"""
      react(s"""{ "jsonrpc": "2.0", "method": "testLog", "params": $params, "re": $re }""")

    /** The response that ends the session, as the worker sends it when its run is over. */
    def sessionResponse(error: Boolean = false, session: Long = id): Unit =
      val body = if error then """"error": {"code": 1}""" else """"result": 0"""
      react(s"""{ "jsonrpc": "2.0", $body, "id": $session }""")

    private def send(method: String, group: String): Unit =
      val params = s"""{"id": $id, "group": "$group"}"""
      react(s"""{ "jsonrpc": "2.0", "method": "$method", "params": $params, "re": $id }""")

    def replies: List[String] = out.toString("UTF-8").linesIterator.toList
  end Fixture

  private object Fixture:
    /** Stands in for a sibling worker that has died, so only identity distinguishes it. */
    val deadStranger: Process = new Process:
      def isAlive(): Boolean = false
      def exitValue(): Int = 1
      def destroy(): Unit = ()

  def exLeaseWithoutAck: Result =
    val f = Fixture(Some(TestQueue(Vector(Vector(0, 1)))))
    f.nextTest(reqId = 7)
    f.nextTest(reqId = 8, done = "0")
    Result
      .assert(f.react.unacknowledgedLeases == Vector("B"))
      .and(Result.assert(f.replies == List(reply("0", 7), reply("1", 8))))
      .log(s"owed=${f.react.unacknowledgedLeases} replies=${f.replies}")

  def exLeaseWithoutAName: Result =
    // The queue and the name vector are built from one `opts.tests`, so an index outside it means
    // the two drifted apart. Naming it vaguely still fails the run; dropping it would report a
    // worker that lost a class as one that owed nothing, which is the pass this whole guard exists
    // to prevent.
    val f = Fixture(Some(TestQueue(Vector(Vector(0, 5)))))
    f.nextTest(reqId = 7)
    f.nextTest(reqId = 8, done = "0")
    val owed = f.react.unacknowledgedLeases
    Result
      .assert(owed.size == 1)
      .and(Result.assert(owed.exists(_.contains("5"))))
      .and(Result.assert(ForkTests.incompleteRun(owed, None).isDefined))
      .log(s"owed=$owed")

  def exFullRun: Result =
    val f = Fixture(Some(TestQueue(Vector(Vector(0, 1)))))
    f.nextTest(reqId = 7)
    f.startGroup("A")
    f.endGroup("A")
    f.nextTest(reqId = 8, done = "0")
    f.startGroup("B")
    f.endGroup("B")
    f.nextTest(reqId = 9, done = "1")
    Result
      .assert(f.react.unacknowledgedLeases.isEmpty)
      .and(Result.assert(f.replies.last == reply("null", 9)))
      .and(Result.assert(f.results.keySet == Set("A", "B")))
      .log(s"replies=${f.replies} results=${f.results.keySet}")

  def exNoTasksProduced: Result =
    // A framework may match a class and then produce no task for it, so no group is ever reported.
    // The worker still acknowledges the lease, and that must not be read as a lost class.
    val f = Fixture(Some(TestQueue(Vector(Vector(0)))))
    f.nextTest(reqId = 7)
    f.nextTest(reqId = 8, done = "0")
    Result
      .assert(f.react.unacknowledgedLeases.isEmpty)
      .and(Result.assert(f.results.isEmpty))
      .log(s"owed=${f.react.unacknowledgedLeases} results=${f.results.keySet}")

  def exTwoFrameworks: Result =
    // A worker runs its frameworks sequentially, so today the two leases of such a class never
    // overlap — but the accounting must not depend on that, or a later change would lose classes.
    val f = Fixture(Some(TestQueue(Vector(Vector(0), Vector(0)))))
    f.nextTest(reqId = 7, framework = 0)
    f.nextTest(reqId = 8, framework = 1)
    val bothOwed = f.react.unacknowledgedLeases
    f.nextTest(reqId = 9, done = "0", framework = 0)
    Result
      .assert(bothOwed == Vector("A", "A"))
      .and(Result.assert(f.react.unacknowledgedLeases == Vector("A")))
      .and(Result.assert(f.replies == List(reply("0", 7), reply("0", 8), reply("null", 9))))
      .log(s"bothOwed=$bothOwed owed=${f.react.unacknowledgedLeases} replies=${f.replies}")

  def exForeignSession: Result =
    val queue = TestQueue(Vector(Vector(0, 1)))
    val f = Fixture(Some(queue))
    f.nextTest(reqId = 7, session = id + 1)
    Result
      .assert(f.react.unacknowledgedLeases.isEmpty)
      .and(Result.assert(f.replies.isEmpty))
      .and(Result.assert(queue.remaining == 2))
      .log(s"replies=${f.replies} remaining=${queue.remaining}")

  def exCleanExitMidSuite: Result =
    // A test calling System.exit(0) leaves its suite started and never ended, while the exit code
    // says nothing is wrong. No queue here, so this is the single-worker path.
    val f = Fixture(None)
    f.startGroup("A")
    f.notifyExit()
    val outcome = f.react.promise.future.value
    Result
      .assert(outcome.exists(_.isFailure))
      .and(Result.assert(f.results.get("A").contains(SuiteResult.Error)))
      .log(s"outcome=$outcome results=${f.results}")

  def exWaitsForTheStream: Result =
    // The watch thread polls, so it can see the process exit while the socket still holds lines
    // nobody has read, and judging then would call a healthy run a System.exit. The suite's end
    // arrives only after the exit is observed, which is what a buffered socket looks like.
    val drained = Promise[Unit]()
    val f = Fixture(None, streamEnd = drained.future)
    f.startGroup("A")
    val reader = Thread(() => {
      f.endGroup("A")
      drained.success(())
      ()
    })
    reader.start()
    f.notifyExit()
    reader.join()
    val outcome = f.react.promise.future.value
    Result
      .assert(outcome.contains(scala.util.Success(0)))
      .log(s"outcome=$outcome results=${f.results}")

  def exExitJudgedOnce: Result =
    // Three callers can reach notifyExit for the same worker: its own watch thread, runWorker's
    // re-check after registering, and the watch thread of any other worker that dies. Two arriving
    // together must not both synthesize a result for the same in-flight suite, or JUnit XML gets
    // two <testsuite> elements for one class.
    val ends = AtomicInteger(0)
    // Holds the first thread inside the report so a second one is caught rather than raced past.
    // The count, not the wait, is what is asserted.
    val arrived = CountDownLatch(2)
    val counting = new TestReportListener:
      def startGroup(name: String): Unit = ()
      def testEvent(event: TestEvent): Unit = ()
      def endGroup(name: String, t: Throwable): Unit = ()
      def endGroup(name: String, result: TestResult): Unit =
        ends.incrementAndGet()
        arrived.countDown()
        arrived.await(2, TimeUnit.SECONDS)
        ()
    val f = Fixture(None, listeners = Seq(counting))
    f.startGroup("A")
    val second = Thread(() => f.notifyExit())
    second.start()
    f.notifyExit()
    second.join()
    Result
      .assert(ends.get() == 1)
      .and(Result.assert(f.react.promise.future.value.exists(_.isFailure)))
      .log(s"endGroup fired ${ends.get()} times")

  def exForeignExit: Result =
    // Exits are broadcast, so a session whose own worker is already dead hears about every other
    // worker's death too, and acting on those re-runs the judgement. Both processes here report
    // isAlive false, so only identity distinguishes them.
    val f = Fixture(None)
    f.startGroup("A")
    f.notifyForeignExit()
    val afterForeign = f.react.promise.future.value
    f.notifyExit()
    Result
      .assert(afterForeign.isEmpty)
      .and(Result.assert(f.react.promise.future.value.exists(_.isFailure)))
      .log(s"afterForeign=$afterForeign afterOwn=${f.react.promise.future.value}")

  def exConcurrentRequests: Result =
    // One reader thread serves a session's requests today, so this cannot happen in production — but
    // the accounting must not be what keeps a class from running twice. The queue's cursor is what
    // does: losing its atomicity hands one index to two callers, and the group reports a pass with
    // one class run twice and another not at all.
    val callers = 8
    val f = Fixture(Some(TestQueue(Vector(Vector(0, 1, 2)))))
    val pool = Executors.newFixedThreadPool(callers)
    // A spin gate rather than a barrier: leaving a barrier is serialised by the barrier's own lock,
    // so the requests would not really overlap.
    val go = AtomicBoolean(false)
    val leased =
      try
        val jobs: Seq[Callable[Unit]] = (0 until callers).map: c =>
          new Callable[Unit]:
            def call(): Unit =
              while !go.get() do Thread.onSpinWait()
              f.nextTest(reqId = c)
        val futures = jobs.map(pool.submit)
        go.set(true)
        futures.foreach(_.get())
        f.replies.flatMap: line =>
          JsonParser.parseString(line).getAsJsonObject().get("result") match
            case r if r.isJsonNull() => None
            case r                   => Some(r.getAsInt())
      finally
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)
        ()
    Result
      .assert(leased.sorted == List(0, 1, 2))
      .and(Result.assert(leased.distinct.size == leased.size))
      .and(Result.assert(f.replies.size == callers))
      // Every class handed out is owed by the worker, whichever caller took it.
      .and(Result.assert(f.react.unacknowledgedLeases.sorted == Vector("A", "B", "C")))
      .log(s"leased=$leased replies=${f.replies.size} owed=${f.react.unacknowledgedLeases}")

  def exListenerThrows: Result =
    // Ending a suite the worker left open runs on the process watch thread, not the one that ran it,
    // and a listener is free to object to that. Without the per-listener guard the first throw would
    // deprive every later listener of the crash, and the run would report an interrupted suite as a
    // pass.
    val seen = mutable.ListBuffer.empty[String]
    val throwing = new TestReportListener:
      def startGroup(name: String): Unit = ()
      def testEvent(event: TestEvent): Unit = ()
      def endGroup(name: String, t: Throwable): Unit = ()
      def endGroup(name: String, result: TestResult): Unit =
        throw RuntimeException("this listener keeps state on another thread")
    val recording = new TestReportListener:
      def startGroup(name: String): Unit = ()
      def testEvent(event: TestEvent): Unit = ()
      def endGroup(name: String, t: Throwable): Unit = ()
      def endGroup(name: String, result: TestResult): Unit =
        seen += s"$name=$result"
        ()
    val f = Fixture(None, listeners = Seq(throwing, recording))
    f.startGroup("A")
    f.notifyExit()
    Result
      .assert(seen.toList == List(s"A=${TestResult.Error}"))
      .and(Result.assert(f.results.get("A").contains(SuiteResult.Error)))
      // Nothing a listener does may stop the promise completing: blockForResponse waits on it.
      .and(Result.assert(f.react.promise.future.value.exists(_.isFailure)))
      .log(s"seen=$seen results=${f.results} outcome=${f.react.promise.future.value}")

  def exErrorResponse: Result =
    // The one line that says the run itself could not be done, rather than that some test failed.
    // Read as a success it settles the session with 0 and the group reports no results at all --
    // which is exactly what a group whose every suite passed looks like from here.
    val f = Fixture(None)
    f.sessionResponse(error = true)
    val outcome = f.react.promise.future.value
    Result
      .assert(outcome.exists(_.isFailure))
      // Carrying the worker's own line, since nothing else says what it objected to.
      .and(Result.assert(outcome.exists(_.failed.get.getMessage.contains("error"))))
      .log(s"outcome=$outcome")

  def exFirstCompletionWins: Result =
    // A worker writes its response once, but exits are broadcast and notifications keep arriving, so
    // more than one thing can try to settle the session. The first outcome is the run's outcome.
    val f = Fixture(None)
    f.startGroup("A")
    f.endGroup("A")
    f.sessionResponse()
    val afterResponse = f.react.promise.future.value
    f.sessionResponse(error = true)
    f.notifyExit()
    Result
      .assert(afterResponse.contains(scala.util.Success(0)))
      .and(Result.assert(f.react.promise.future.value.contains(scala.util.Success(0))))
      .log(s"afterResponse=$afterResponse final=${f.react.promise.future.value}")

  def exNonZeroExit: Result =
    // Nothing else covers the exit code itself: a worker killed by the OOM killer or by a bad JVM
    // option finishes no suite, so only the code says the run is not a pass.
    val f = Fixture(None, exitCode = 3)
    f.notifyExit()
    val outcome = f.react.promise.future.value
    Result
      .assert(outcome.exists(_.isFailure))
      .and(Result.assert(outcome.exists(_.failed.get.getMessage.contains("3"))))
      .log(s"outcome=$outcome")

  def exDeathPoisonsTheQueue: Result =
    // The other workers of the group are still leasing. Left serving, they would pick up the classes
    // of a group that has already failed and report them into a run that is going to be thrown away.
    val queue = TestQueue(Vector(Vector(0, 1, 2)))
    val f = Fixture(Some(queue), exitCode = 1)
    f.nextTest(reqId = 7)
    val servingBefore = queue.hasWork
    f.notifyExit()
    Result
      .assert(servingBefore)
      .and(Result.assert(!queue.hasWork))
      .and(Result.assert(queue.lease(0).isEmpty))
      // The units are still counted as unrun, which is what fails the group.
      .and(Result.assert(queue.remaining == 2))
      .log(s"before=$servingBefore after=${queue.hasWork} remaining=${queue.remaining}")

  def exForeignNotification: Result =
    // Several workers share the registry, so until a connection is bound a sibling's notifications
    // reach this listener too. Recording them would credit this session with another's suites, and
    // it has to be a silent drop: a sibling is not a protocol disagreement, so treating one as
    // something this session could not account for would fail a run over another worker's traffic.
    val f = Fixture(None)
    val params = s"""{"id": ${id + 1}, "group": "A"}"""
    def foreign(method: String, body: String = params): Unit =
      f.react(s"""{ "jsonrpc": "2.0", "method": "$method", "params": $body, "re": ${id + 1} }""")
    foreign("startTestGroup")
    foreign("endTestGroup")
    foreign("forkError", s"""{"id": ${id + 1}, "error": {"message": "not ours"}}""")
    Result
      .assert(f.results.isEmpty)
      .and(Result.assert(f.react.unrecordedFailure.isEmpty))
      // A sibling's forkError must not fail this session either.
      .and(Result.assert(f.react.promise.future.value.isEmpty))
      .log(
        s"results=${f.results.keySet} unrecorded=${f.react.unrecordedFailure} " +
          s"promise=${f.react.promise.future.value}"
      )

  def exForeignResponse: Result =
    // Nothing stops a sibling's response reaching this listener, and taking it would end this session
    // before its own worker had run anything, reporting a group of missing classes as a pass.
    val f = Fixture(None)
    f.sessionResponse(session = id + 1)
    val afterForeign = f.react.promise.future.value
    f.sessionResponse()
    Result
      .assert(afterForeign.isEmpty)
      .and(Result.assert(f.react.promise.future.value.contains(scala.util.Success(0))))
      .log(s"afterForeign=$afterForeign final=${f.react.promise.future.value}")

  def exLogLevels: Result =
    // Everything a test prints, and every reason a framework gives for failing, reaches the user
    // through these lines alone. A level dropped or downgraded loses that silently.
    val seen = mutable.ListBuffer.empty[(Level.Value, String)]
    val capture = new Logger:
      def trace(t: => Throwable): Unit = ()
      def success(message: => String): Unit = ()
      def log(level: Level.Value, message: => String): Unit =
        seen.append(level -> message)
    val f = Fixture(None, log = capture)
    f.testLog("Error", "boom")
    f.testLog("Warn", "careful")
    f.testLog("Info", "running")
    f.testLog("Debug", "detail")
    // A sibling's line, filtered by the id inside the notification rather than by the envelope.
    f.testLog("Error", "someone else's", session = id + 1)
    Result
      .assert(
        seen.toList == List(
          Level.Error -> "boom",
          Level.Warn -> "careful",
          Level.Info -> "running",
          Level.Debug -> "detail",
        )
      )
      .log(s"seen=${seen.toList}")

  def exProgressBecomesResult: Result =
    // A suite's events are what its SuiteResult is made of, so dropping them turns a suite full of
    // failures into an empty pass.
    val f = Fixture(None)
    f.startGroup("A")
    f.testProgress("A", "Failure")
    f.testProgress("A", "Success")
    f.endGroup("A")
    val result = f.results.get("A")
    Result
      .assert(result.exists(_.result == TestResult.Failed))
      .and(Result.assert(result.exists(_.failureCount == 1)))
      .and(Result.assert(result.exists(_.passedCount == 1)))
      .log(s"result=${result.map(r => (r.result, r.failureCount, r.passedCount))}")

  def exOneClassTwoFrameworks: Result =
    // Why `spreadable` keeps such a group in one JVM: the class runs once per framework, and that
    // one JVM reports a start/end pair per run under the one name. Both verdicts have to survive —
    // keeping the last would let a pass under the second framework bury a failure under the first.
    val f = Fixture(None)
    f.startGroup("A")
    f.testProgress("A", "Failure")
    f.endGroup("A")
    val afterFirst = f.results.get("A").map(_.result)
    f.startGroup("A")
    f.testProgress("A", "Success")
    f.endGroup("A")
    val result = f.results.get("A")
    Result
      .assert(afterFirst.contains(TestResult.Failed))
      .and(Result.assert(result.exists(_.result == TestResult.Failed)))
      .and(Result.assert(result.exists(_.failureCount == 1)))
      .and(Result.assert(result.exists(_.passedCount == 1)))
      .log(s"afterFirst=$afterFirst result=${result.map(r => (r.result, r.failureCount))}")

  def exEventsConsumedOnEnd: Result =
    // Taken from the buffer, not copied out of it. What is left behind is counted again by the next
    // end for that name, and held for as long as the run lasts — an event keeps its throwable, and a
    // throwable keeps the test class loader and its open jars alive.
    val f = Fixture(None)
    f.startGroup("A")
    f.testProgress("A", "Failure")
    f.endGroup("A")
    // A late event with no start of its own, which a framework reporting after its suite closed
    // produces. It must stand alone rather than joining the events already counted.
    f.testProgress("A", "Success")
    f.endGroup("A")
    val result = f.results.get("A")
    Result
      .assert(result.exists(_.failureCount == 1))
      .and(Result.assert(result.exists(_.passedCount == 1)))
      .log(s"result=${result.map(r => (r.failureCount, r.passedCount))}")

  def exUnknownShape: Result =
    // Valid JSON that is none of the three shapes: no method, so not a request or notification, and
    // no id, so not this session's response. It carries nothing to record, and above all it must not
    // be mistaken for the response — completing the promise here would end the run at whatever had
    // been reported so far and call that the group's result.
    val f = Fixture(None)
    f.react("""{ "jsonrpc": "2.0" }""")
    f.react("""{ "jsonrpc": "2.0", "method": "startTestGroup" }""")
    Result
      .assert(f.react.promise.future.value.isEmpty)
      .and(Result.assert(f.results.isEmpty))
      .and(Result.assert(f.react.unrecordedFailure.isEmpty))
      .log(s"promise=${f.react.promise.future.value} results=${f.results.keySet}")

  def exUnservedRequest: Result =
    // The worker blocks on the reply, so silence here is a ten-minute stall ending in "Lost contact
    // with sbt". sbt gave this worker its session id, so a method it has no case for means the two
    // sides disagree on the protocol, and the run cannot be vouched for either way.
    val f = Fixture(None)
    f.react(s"""{ "jsonrpc": "2.0", "method": "nextSuite", "params": {"id": $id}, "id": 7 }""")
    Result
      .assert(f.react.unrecordedFailure.exists(_.contains("nextSuite")))
      .and(Result.assert(f.replies == Nil))
      .log(s"unrecorded=${f.react.unrecordedFailure} replies=${f.replies}")

  def exUnrecordableSuite: Result =
    // The event's throwable is unset, which SuiteResult dereferences.
    val f = Fixture(None)
    f.startGroup("A")
    f.brokenProgress("A")
    f.endGroup("A")
    Result
      .assert(f.results.get("A").isEmpty)
      .and(Result.assert(f.react.unrecordedFailure.isDefined))
      .log(s"results=${f.results.keySet} unrecorded=${f.react.unrecordedFailure}")

  def exUnattributableResult: Result =
    // How a whole run disappears: every filter here used to drop in silence, so a worker whose
    // suites sbt could not attribute reported nothing at all and the group passed with no tests run.
    // The envelope names this session and the payload names another, which one worker cannot do.
    val f = Fixture(None)
    val params = s"""{"id": ${id + 1}, "group": "A"}"""
    f.react(s"""{ "jsonrpc": "2.0", "method": "startTestGroup", "params": $params, "re": $id }""")
    Result
      .assert(f.results.isEmpty)
      .and(Result.assert(f.react.unrecordedFailure.isDefined))
      .and(Result.assert(f.react.unrecordedFailure.exists(_.contains("startTestGroup"))))
      .log(s"unrecorded=${f.react.unrecordedFailure}")

  def exUnhandledMethod: Result =
    // A worker sending something this sbt has no case for means the two disagree about the protocol.
    // Ignoring it cannot be safe: the notification may be the one carrying a suite's results.
    val f = Fixture(None)
    val params = s"""{"id": $id, "group": "A"}"""
    f.react(s"""{ "jsonrpc": "2.0", "method": "somethingNewer", "params": $params, "re": $id }""")
    Result
      .assert(f.react.unrecordedFailure.exists(_.contains("somethingNewer")))
      .log(s"unrecorded=${f.react.unrecordedFailure}")

  def exPlainOutput: Result =
    // A line this cannot parse is the only way anything the protocol does not carry reaches the
    // user, so it has to be logged — not quietly reshaped into a message of no recognised shape and
    // dropped. Asserting only that nothing was recorded let exactly that through.
    val seen = mutable.ListBuffer.empty[(Level.Value, String)]
    val capture = new Logger:
      def trace(t: => Throwable): Unit = ()
      def success(message: => String): Unit = ()
      def log(level: Level.Value, message: => String): Unit =
        seen.append(level -> message)
    val f = Fixture(None, log = capture)
    f.react("Downloading some dependency...")
    f.react("")
    Result
      .assert(seen.contains(Level.Info -> "Downloading some dependency..."))
      .and(Result.assert(f.react.unrecordedFailure.isEmpty))
      .and(Result.assert(f.results.isEmpty))
      .log(s"seen=$seen unrecorded=${f.react.unrecordedFailure}")

  def exCleanExitAfterSuites: Result =
    // The other side of the same check: an ordinary run ends with every suite closed.
    val f = Fixture(None)
    f.startGroup("A")
    f.endGroup("A")
    f.notifyExit()
    val outcome = f.react.promise.future.value
    Result
      .assert(outcome.contains(scala.util.Success(0)))
      .log(s"outcome=$outcome")
end ReactTest
