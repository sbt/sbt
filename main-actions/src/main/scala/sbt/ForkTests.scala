/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import org.scalasbt.shadedgson.com.google.gson.{ JsonObject, JsonParser }
import testing.{ Logger as _, Task as _, * }
import java.io.*
import java.util.{ ArrayList, Set as JSet }
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import Tests.{ Output as TestOutput, * }
import sbt.util.Logger
import sbt.ConcurrentRestrictions.Tag
import sbt.protocol.testing.*
import sbt.internal.{ TestQueue, WorkerExchange, WorkerProxy, WorkerResponseListener }
import sbt.internal.util.Util.*
import sbt.internal.util.{ MessageOnlyException, Terminal as UTerminal }
import sbt.internal.worker1.*
import xsbti.{ FileConverter, HashedVirtualFileRef }
import scala.collection.mutable
import scala.collection.concurrent.TrieMap
import scala.concurrent.{ Await, Promise }
import scala.concurrent.duration.Duration
import scala.util.Random
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters.*
import scala.sys.process.Process
import sbt.internal.WorkerConnection

/**
 * This implements forked testing, in cooperation with the worker CLI,
 * which was previously called test-agent.jar.
 */
private[sbt] object ForkTests:
  val r = Random()

  /** Gson instances are thread-safe, so one serves every session. */
  private[sbt] val gson = WorkerMain.mkGson()

  /**
   * virtualClasspath can be controlled by setting
   * Test / classLoaderLayeringStrategy to ClassLoaderLayeringStrategy.Raw.
   */
  def apply(
      runners: Map[TestFramework, Runner],
      opts: ProcessedOptions,
      config: Execution,
      classpath: Seq[HashedVirtualFileRef],
      converter: FileConverter,
      fork: ForkOptions,
      log: Logger,
      forkedParallel: Boolean,
      parallelism: Option[Int],
      virtualClasspath: Boolean,
      frameworks: Map[TestFramework, Framework],
      maxWorkers: Int,
      tags: (Tag, Int)*
  ): Task[TestOutput] = {
    import std.TaskExtra.*
    val dummyLoader =
      this.getClass.getClassLoader // can't provide the loader for test classes, which is in another jvm
    def all(work: Seq[ClassLoader => Unit]) = work.fork(f => f(dummyLoader))

    // The worker sends indices into testRunners, so the queue's framework indexing shares this order.
    val runnerSeq = runners.toSeq
    lazy val units = byFramework(runnerSeq, frameworks, opts.tests)
    // `parallelExecution`'s question, not `testForkedParallel`'s: that one governs only what a
    // worker does with the classes it holds, so turning it off is a reason to spread, not to pin.
    val n = workerCount(maxWorkers, config.parallel, units, log)
    // What each worker then does with the classes it holds. ANDed, so `parallelExecution := false` still
    // means one at a time inside the single JVM it leaves.
    val workerParallel = config.parallel && forkedParallel

    // Identical for every worker: queue mode is group-wide (n > 1 iff a queue exists).
    lazy val request = testRequestJson(
      runnerSeq,
      opts,
      classpath,
      converter,
      workerParallel,
      parallelism,
      virtualClasspath,
      queueMode = n > 1
    )
    lazy val extraCp =
      if virtualClasspath then Nil else classpath.map(vf => converter.toPath(vf).toFile())
    lazy val testNames = opts.tests.map(_.name)

    // A group's first JVM runs ahead of ordinary tasks, the ones that only spread its queue wider
    // behind them by index, so a freed slot goes to a group with no JVM before a second JVM for a
    // group that has one. Only orders what a CompletionService is already holding back -- `submit`
    // runs a task as soon as its tags validate -- so it governs freed slots, not idle ones. Mill's
    // schedule: `priority = if (processIndex == 0) -1 else processIndex`.
    def worker(queue: Option[TestQueue], index: Int) =
      std.TaskExtra
        .task(
          queue match
            // Skip the ~1s JVM spawn when earlier workers already drained the queue.
            case Some(q) if !q.hasWork =>
              TestOutput(TestResult.Passed, Map.empty[String, SuiteResult], Iterable.empty)
            case q =>
              runWorker(request, testNames, opts.testListeners, extraCp, fork, log, q)
        )
        .withPriority(if index == 0 then -1 else index)
        .tagw(config.tags*)
        .tagw(tags*)

    val main =
      if opts.tests.isEmpty then
        constant(TestOutput(TestResult.Passed, Map.empty[String, SuiteResult], Iterable.empty))
      else
        val queue = if n == 1 then None else Some(TestQueue(units))
        val listeners = testsListeners(opts)
        // doInit/doComplete fire once per group. Only the workers are tagged, so a Tags.limit on
        // ForkedTestGroup counts JVMs without counting the group twice.
        std.TaskExtra
          .task(listeners.foreach(_.doInit()))
          .flatMap: _ =>
            Tests
              .foldTasks(
                queue.fold(Seq(worker(None, 0)))(_ => (0 until n).map(i => worker(queue, i))),
                true
              )
              .map: out =>
                queue
                  .flatMap(q => undrainedQueue(q.remaining))
                  .foreach: why =>
                    throw MessageOnlyException(why)
                listeners.foreach(_.doComplete(out.overall))
                out
    main.dependsOn(all(opts.setup)*) flatMap { results =>
      all(opts.cleanup).join.map(_ => results)
    }
  }

  /**
   * How many forked JVMs a group may spread over. `units` is by-name because the default path never
   * needs it, and matching fingerprints for a group that will not spread is wasted work.
   */
  private[sbt] def workerCount(
      maxWorkers: Int,
      parallel: Boolean,
      units: => Vector[Vector[Int]],
      log: Logger
  ): Int =
    if maxWorkers <= 1 then 1
    else if !parallel then
      log.debug(
        "testForkedWorkStealing is on, but parallel test execution is off for this group, so it " +
          "will run in one JVM."
      )
      1
    else if !spreadable(units) then
      log.info(
        "Not spreading this test group over several forked JVMs: some of its classes match more " +
          "than one test framework, and the two runs of such a class would overlap. Running the " +
          "group in one JVM instead."
      )
      1
    else
      val unitCount = units.map(_.size).sum
      val workers = math.max(1, math.min(maxWorkers, unitCount))
      if workers > 1 then
        log.info(s"Spreading $unitCount test classes over up to $workers forked JVMs.")
      workers

  /**
   * Whether a group's classes may be spread over several JVMs: false when any class matches more
   * than one framework, since its two runs could then overlap and put two writers on one
   * `TEST-<suite>.xml`.
   */
  private[sbt] def spreadable(units: Vector[Vector[Int]]): Boolean =
    val flat = units.flatten
    flat.distinct.size == flat.size

  private def testsListeners(opts: ProcessedOptions): Vector[TestsListener] =
    opts.testListeners.flatMap:
      case tl: TestsListener => tl.some
      case _                 => none[TestsListener]

  /**
   * For each framework index, the `taskDefs` indices that framework matches. A relation, not a
   * partition: a class matching two frameworks appears in both and runs under each.
   */
  private[sbt] def byFramework(
      runnerSeq: Seq[(TestFramework, Runner)],
      frameworks: Map[TestFramework, Framework],
      tests: Vector[TestDefinition]
  ): Vector[Vector[Int]] =
    runnerSeq.toVector.map: (tf, _) =>
      val prints = frameworks.get(tf).map(TestFramework.getFingerprints).getOrElse(Nil)
      tests.zipWithIndex.collect {
        case (t, i) if prints.exists(p => TestFramework.matches(p, t.fingerprint)) => i
      }

  private[sbt] def testRequestJson(
      runnerSeq: Seq[(TestFramework, Runner)],
      opts: ProcessedOptions,
      classpath: Seq[HashedVirtualFileRef],
      converter: FileConverter,
      parallel: Boolean,
      parallelism: Option[Int],
      virtualClasspath: Boolean,
      queueMode: Boolean
  ): String =
    val taskdefs = opts.tests.map: t =>
      new TaskDef(
        t.name,
        forkFingerprint(t.fingerprint),
        t.explicitlySpecified,
        t.selectors
      )
    val testRunners = runnerSeq.map: (testFramework, mainRunner) =>
      TestInfo.TestRunner(
        ArrayList(testFramework.implClassNames.asJava),
        ArrayList(mainRunner.args().toList.asJava),
        ArrayList(mainRunner.remoteArgs().toList.asJava)
      )
    val cpList =
      if virtualClasspath then
        ArrayList[FilePath](
          (classpath
            .map: vf =>
              FilePath(converter.toPath(vf).toUri(), vf.contentHashStr()))
            .asJava
        )
      else ArrayList[FilePath]()
    val param = TestInfo(
      true, /* jvm */
      RunInfo.JvmRunInfo(
        ArrayList(),
        cpList,
        "",
        false /*connectInput*/,
      ),
      null,
      UTerminal.isAnsiSupported,
      parallel,
      parallelism.map(Integer.valueOf).orNull,
      ArrayList(taskdefs.asJava),
      ArrayList(testRunners.asJava),
      queueMode,
    )
    gson.toJson(param, param.getClass)

  private def runWorker(
      request: String,
      testNames: Vector[String],
      listeners: Seq[TestReportListener],
      extraCp: Seq[File],
      fork: ForkOptions,
      log: Logger,
      queue: Option[TestQueue]
  ): TestOutput = {
    // Two threads record into it: the reader thread as groups end, and the watch thread
    // synthesising results for a dying worker.
    val resultsAcc = TrieMap.empty[String, SuiteResult]
    val randomId = r.nextLong()
    val w = WorkerExchange.startWorker(fork, extraCp, WorkerConnection.Tcp)
    val wl = React(randomId, log, listeners, resultsAcc, w, queue, testNames)
    try
      // Both, in this order: the registry carries exits; bind routes this connection's lines here.
      WorkerExchange.registerListener(wl)
      w.bind(wl)
      // An exit can be broadcast before this listener exists to hear it.
      if !w.process.isAlive() then wl.notifyExit(w.process)
      w.println(jsonRpcRequest(randomId, "test", request))
      if wl.blockForResponse() != 0 then throw MessageOnlyException("Forked test harness failed")
      incompleteRun(wl.unacknowledgedLeases, wl.unrecordedFailure).foreach: why =>
        // The others are still leasing from a group that is about to fail.
        queue.foreach(_.poison())
        throw MessageOnlyException(why)
      TestOutput(overall(resultsAcc.values.map(_.result)), resultsAcc.toMap, Iterable.empty)
    finally
      WorkerExchange.unregisterListener(wl)
      w.close()
  } // end runWorker

  /**
   * Why a group's queue was left with work in it, if it was. The workers of a group all end without
   * failing only when each found the queue empty, so units still in it mean classes nobody ran.
   */
  private[sbt] def undrainedQueue(remaining: Int): Option[String] =
    if remaining > 0 then
      Some(s"$remaining test classes were never run because the forked test workers exited early")
    else None

  /**
   * Why a worker's report cannot be trusted, if it cannot. Both would otherwise read as a pass: an
   * unrun class leaves no trace, and an unrecorded result leaves its suite absent.
   */
  private[sbt] def incompleteRun(
      unrun: Vector[String],
      unrecorded: Option[String]
  ): Option[String] =
    if unrun.nonEmpty then
      // A worker can exit cleanly without running a leased class: the framework failed to load, or
      // the runner threw. The queue still counts the class as handed out.
      Some(s"A forked test worker exited without running ${unrun.distinct.sorted.mkString(", ")}")
    else
      unrecorded.map: why =>
        s"A forked test worker reported test results sbt could not record, so this run is " +
          s"incomplete: $why"

  private def jsonRpcRequest(id: Long, method: String, params: String): String =
    s"""{ "jsonrpc": "2.0", "method": "$method", "params": $params, "id": $id }"""

  /**
   * The kinds of line a worker sends. Dispatching on `method` first keeps a worker's `nextTest`
   * request from being mistaken for the session response.
   */
  private[sbt] enum Shape:
    case Request, Notification, Response, Unknown

  private[sbt] def shapeOf(o: JsonObject): Shape =
    if o.has("method") then
      if o.has("id") then Shape.Request
      else if o.has("re") then Shape.Notification
      else Shape.Unknown
    else if o.has("id") then Shape.Response
    else Shape.Unknown

  private def forkFingerprint(f: Fingerprint): Fingerprint & Serializable =
    f match
      case s: SubclassFingerprint  => ForkTestMain.SubclassFingerscan(s)
      case a: AnnotatedFingerprint => ForkTestMain.AnnotatedFingerscan(a)
      case _                       => sys.error("Unknown fingerprint type: " + f.getClass)
end ForkTests

private[sbt] class React(
    id: Long,
    log: Logger,
    listeners: Seq[TestReportListener],
    results: mutable.Map[String, SuiteResult],
    proxy: WorkerProxy,
    queue: Option[TestQueue],
    testNames: Vector[String]
) extends WorkerResponseListener:
  private val process: Process = proxy.process

  /** Suites started but not yet finished, so a dying worker can report what it was running. */
  private val startedGroups: JSet[String] = ConcurrentHashMap.newKeySet[String]()

  /**
   * Leases this worker has not acknowledged finishing. Keyed by (framework index, `taskDefs` index)
   * rather than class name: a class matching two frameworks is leased once per framework.
   */
  private val outstandingLeases: JSet[(Int, Int)] =
    ConcurrentHashMap.newKeySet[(Int, Int)]()

  /**
   * The class a lease stands for. An index this session cannot name still names something: dropping
   * it would turn a class nobody ran into a run with nothing owed, which reads as a pass.
   */
  private def nameOf(index: Int): String =
    if index >= 0 && index < testNames.length then testNames(index)
    else s"an unidentified test class at index $index"

  /** The first thing this session could not record, which makes the run's report incomplete. */
  private val unrecorded = AtomicReference[String](null)
  def unrecordedFailure: Option[String] = Option(unrecorded.get())

  /**
   * Notified one by one, so a listener that throws costs neither the others nor the record. At error,
   * as `TestFramework.safeForeach` does in-process: a throw here has lost that suite's report.
   */
  private def tell(group: String)(f: TestReportListener => Unit): Unit =
    listeners.foreach: l =>
      try f(l)
      catch
        case NonFatal(e) =>
          log.trace(e)
          log.error(s"Listener ${l.getClass.getName} could not record $group: $e")

  private val g = ForkTests.gson
  val promise: Promise[Int] = Promise()

  /** Events per group, for [[SuiteResult]]. Touched only by this connection's reader thread. */
  private val progressEvents = mutable.Map.empty[String, mutable.ArrayBuffer[testing.Event]]
  override def apply(line: String): Unit =
    // Workers print ordinary output down this connection too.
    messageOf(line) match
      case None    => log.info(line)
      case Some(o) =>
        try dispatch(o, line)
        catch case NonFatal(e) => cannotAccountFor(s"$e")

  /**
   * Notes that a worker reported something this session could not act on. Dropped in silence, sbt
   * cannot tell a worker that found nothing from one whose results it threw away.
   */
  private def cannotAccountFor(what: String): Unit =
    log.error(s"Could not record what a forked test worker reported: $what")
    unrecorded.compareAndSet(null, what)
    ()

  /**
   * A result-bearing notification whose envelope named this session but whose payload named another.
   * A worker writes both from one id, so they disagree only if the two sides of the protocol do.
   */
  private def foreignPayload(method: String, payloadId: Long): Unit =
    cannotAccountFor(s"a $method naming session $payloadId arrived on session $id's channel")

  private def messageOf(line: String): Option[JsonObject] =
    try Some(JsonParser.parseString(line).getAsJsonObject())
    catch case NonFatal(_) => None

  private def dispatch(o: JsonObject, line: String): Unit =
    ForkTests.shapeOf(o) match
      case ForkTests.Shape.Request      => serveRequest(o)
      case ForkTests.Shape.Notification =>
        if o.getAsJsonPrimitive("re").getAsLong() == id then processNotification(o)
        else ()
      case ForkTests.Shape.Response =>
        if o.getAsJsonPrimitive("id").getAsLong() == id then
          if o.has("error") then promise.tryFailure(new RuntimeException(line))
          else promise.trySuccess(0)
          ()
        else ()
      case ForkTests.Shape.Unknown => ()

  private def serveRequest(o: JsonObject): Unit =
    val method = o.getAsJsonPrimitive("method").getAsString()
    val reqId = o.getAsJsonPrimitive("id").getAsLong()
    val params = o.getAsJsonObject("params")
    if params == null || !params.has("id") then ()
    else if params.getAsJsonPrimitive("id").getAsLong() != id then ()
    else
      method match
        case "nextTest" =>
          val framework = params.getAsJsonPrimitive("framework").getAsInt()
          completeLease(framework, params)
          val result = leaseFor(framework).fold("null")(_.toString)
          proxy.println(s"""{ "jsonrpc": "2.0", "result": $result, "id": $reqId }""")
        // The worker blocks on the reply, so silence here stalls it until its ten-minute rpc
        // timeout. Naming the method says which side of the protocol the two disagree on.
        case other => cannotAccountFor(s"a request sbt does not serve: $other")

  /**
   * Ticks off the class reported in `done`. Only the worker knows a lease is finished: a framework
   * may produce no task at all for a class it matched.
   */
  private def completeLease(framework: Int, params: JsonObject): Unit =
    val done = params.get("done")
    if done != null && done.isJsonPrimitive() then
      outstandingLeases.remove((framework, done.getAsInt()))
      ()

  private def leaseFor(framework: Int): Option[Int] =
    val leased = queue.flatMap(_.lease(framework))
    leased.foreach(i => outstandingLeases.add((framework, i)))
    leased

  def unacknowledgedLeases: Vector[String] =
    outstandingLeases.asScala.toVector.map((_, i) => nameOf(i))

  /** Reports the suites still in flight when the process died; unrecorded they read as a pass. */
  private def synthesizeErrorsForInFlight(exitCode: Int): Unit =
    val inFlight = startedGroups.toArray(Array.empty[String]).toVector
    inFlight.foreach: group =>
      log.error(
        s"Test suite $group was interrupted: the forked test JVM exited with code $exitCode"
      )
      results += group -> SuiteResult.Error
      tell(group)(_.endGroup(group, TestResult.Error))
      startedGroups.remove(group)
      ()

  /**
   * Judges a dead worker, at most once, and only its own: exits are broadcast, so `p` filters out
   * other workers' deaths. `synchronized` because the watch thread and `runWorker`'s re-check can
   * arrive together; [[apply]] must stay free of the monitor, since `awaitStreamEnd` waits for the
   * thread that calls it.
   */
  override def notifyExit(p: Process): Unit =
    if p eq process then
      synchronized:
        if !process.isAlive() && !promise.isCompleted then
          // Suites can still be buffered in the socket; judging before they drain would misreport
          // a healthy run.
          proxy.awaitStreamEnd()
          if !promise.isCompleted then
            val exitCode = process.exitValue()
            val inFlight = !startedGroups.isEmpty()
            // Completing the promise releases blockForResponse; reporting must never prevent it.
            val failure: Option[String] =
              if exitCode != 0 then Some(s"Forked test process exited with code $exitCode")
              else if inFlight then
                // The worker ends every suite it starts, so a clean exit with one open means a
                // test called System.exit.
                Some(
                  "Forked test process exited cleanly while test suites were still running, " +
                    "which usually means a test called System.exit"
                )
              else None
            try
              if failure.isDefined then
                queue.foreach(_.poison())
                synthesizeErrorsForInFlight(exitCode)
            catch case NonFatal(e) => log.trace(e)
            finally
              failure match
                case Some(msg) => promise.tryFailure(new RuntimeException(msg))
                case None      => promise.trySuccess(exitCode)
              ()

  def processNotification(o: JsonObject): Unit =
    val method = o.getAsJsonPrimitive("method").getAsString()
    method match
      case "testLog" =>
        val params = o.getAsJsonObject("params")
        val info = g.fromJson[TestLogInfo](params, classOf[TestLogInfo])
        if info.id == id then
          info.tag match
            case ForkTags.Error => log.error(info.message)
            case ForkTags.Warn  => log.warn(info.message)
            case ForkTags.Info  => log.info(info.message)
            case ForkTags.Debug => log.debug(info.message)
            case _              => ()
        else ()
      case "startTestGroup" =>
        val params = o.getAsJsonObject("params")
        val info =
          g.fromJson[ForkTestMain.ForkGroupStart](params, classOf[ForkTestMain.ForkGroupStart])
        if info.id == id then
          progressEvents(info.group) = mutable.ArrayBuffer.empty
          startedGroups.add(info.group)
          tell(info.group)(_.startGroup(info.group))
        else foreignPayload("startTestGroup", info.id)
      case "testProgress" =>
        val params = o.getAsJsonObject("params")
        val info =
          g.fromJson[ForkTestMain.ForkEventsInfo](params, classOf[ForkTestMain.ForkEventsInfo])
        if info.id == id then
          val buf = progressEvents.getOrElseUpdate(info.group, mutable.ArrayBuffer.empty)
          for e <- info.events.asScala do
            buf += e
            tell(info.group)(_.testEvent(TestEvent(Seq(e))))
        else foreignPayload("testProgress", info.id)
      case "endTestGroup" =>
        val params = o.getAsJsonObject("params")
        val info =
          g.fromJson[ForkTestMain.ForkGroupEnd](params, classOf[ForkTestMain.ForkGroupEnd])
        if info.id == id then
          val events = progressEvents.remove(info.group).getOrElse(mutable.ArrayBuffer.empty).toSeq
          val suiteResult = SuiteResult(events)
          // Added to, never replacing: a class matching two frameworks runs once per framework in the
          // one JVM `spreadable` keeps it in, and replacing would keep only the last verdict.
          results += info.group -> results.get(info.group).fold(suiteResult)(_ + suiteResult)
          startedGroups.remove(info.group)
          tell(info.group)(_.endGroup(info.group, suiteResult.result))
        else foreignPayload("endTestGroup", info.id)
      case "forkError" =>
        val params = o.getAsJsonObject("params")
        val info =
          g.fromJson[ForkTestMain.ForkErrorInfo](params, classOf[ForkTestMain.ForkErrorInfo])
        if info.id == id then
          log.trace(info.error)
          promise.tryFailure(info.error)
          ()
        else foreignPayload("forkError", info.id)
      case other => cannotAccountFor(s"a notification sbt does not handle: $other")

  def blockForResponse(): Int =
    Await.result(promise.future, Duration.Inf)
end React
