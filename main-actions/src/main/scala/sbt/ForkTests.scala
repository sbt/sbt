/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import org.scalasbt.shadedgson.com.google.gson.{ JsonObject, JsonParser, JsonSyntaxException }
import testing.{ Logger as _, Task as _, * }
import java.io.*
import java.util.ArrayList
import Tests.{ Output as TestOutput, * }
import sbt.util.Logger
import sbt.ConcurrentRestrictions.Tag
import sbt.protocol.testing.*
import sbt.internal.{ WorkerExchange, WorkerResponseListener }
import sbt.internal.util.Util.*
import sbt.internal.util.{ MessageOnlyException, Terminal as UTerminal }
import sbt.internal.worker1.*
import xsbti.{ FileConverter, HashedVirtualFileRef }
import scala.collection.mutable
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

  def apply(
      runners: Map[TestFramework, Runner],
      opts: ProcessedOptions,
      config: Execution,
      classpath: Seq[HashedVirtualFileRef],
      converter: FileConverter,
      fork: ForkOptions,
      log: Logger,
      parallelism: Option[Int],
      tags: (Tag, Int)*
  ): Task[TestOutput] = {
    import std.TaskExtra.*
    val dummyLoader =
      this.getClass.getClassLoader // can't provide the loader for test classes, which is in another jvm
    def all(work: Seq[ClassLoader => Unit]) = work.fork(f => f(dummyLoader))

    val main =
      if opts.tests.isEmpty then
        constant(TestOutput(TestResult.Passed, Map.empty[String, SuiteResult], Iterable.empty))
      else
        mainTestTask(runners, opts, classpath, converter, fork, log, config.parallel, parallelism)
          .tagw(
            config.tags*
          )
    main.tagw(tags*).dependsOn(all(opts.setup)*) flatMap { results =>
      all(opts.cleanup).join.map(_ => results)
    }
  }

  def apply(
      runners: Map[TestFramework, Runner],
      tests: Vector[TestDefinition],
      config: Execution,
      classpath: Seq[HashedVirtualFileRef],
      converter: FileConverter,
      fork: ForkOptions,
      log: Logger,
      parallelism: Option[Int],
      tags: (Tag, Int)*
  ): Task[TestOutput] = {
    val opts = processOptions(config, tests, log)
    apply(runners, opts, config, classpath, converter, fork, log, parallelism, tags*)
  }

  def apply(
      runners: Map[TestFramework, Runner],
      tests: Vector[TestDefinition],
      config: Execution,
      classpath: Seq[HashedVirtualFileRef],
      converter: FileConverter,
      fork: ForkOptions,
      log: Logger,
      parallelism: Option[Int],
      tag: Tag
  ): Task[TestOutput] = {
    apply(runners, tests, config, classpath, converter, fork, log, parallelism, tag -> 1)
  }

  private def mainTestTask(
      runners: Map[TestFramework, Runner],
      opts: ProcessedOptions,
      classpath: Seq[HashedVirtualFileRef],
      converter: FileConverter,
      fork: ForkOptions,
      log: Logger,
      parallel: Boolean,
      parallelism: Option[Int]
  ): Task[TestOutput] =
    std.TaskExtra.task {
      val testListeners = opts.testListeners.flatMap:
        case tl: TestsListener => tl.some
        case _                 => none[TestsListener]
      val resultsAcc = mutable.Map.empty[String, SuiteResult]
      val randomId = r.nextLong()
      def testOutputResult =
        TestOutput(
          overall(resultsAcc.values.map(_.result)),
          resultsAcc.toMap,
          Iterable.empty
        )
      val taskdefs = opts.tests.map: t =>
        new TaskDef(
          t.name,
          forkFingerprint(t.fingerprint),
          t.explicitlySpecified,
          t.selectors
        )
      val testRunners = runners.toSeq.map: (testFramework, mainRunner) =>
        TestInfo.TestRunner(
          ArrayList(testFramework.implClassNames.asJava),
          ArrayList(mainRunner.args().toList.asJava),
          ArrayList(mainRunner.remoteArgs().toList.asJava)
        )
      val g = WorkerMain.mkGson()
      // virtualize classloading by using ClassLoader
      val useClassLoader = true
      val cpList = ArrayList[FilePath](
        (classpath
          .map: vf =>
            FilePath(converter.toPath(vf).toUri(), vf.contentHashStr()))
          .asJava
      )
      val cpFiles =
        classpath.map: vf =>
          converter.toPath(vf).toFile()
      val param = TestInfo(
        true, /* jvm */
        RunInfo.JvmRunInfo(
          ArrayList(),
          if useClassLoader then cpList else ArrayList(),
          "",
          false /*connectInput*/,
        ),
        null,
        UTerminal.isAnsiSupported,
        parallel,
        parallelism.map(Integer.valueOf).orNull,
        ArrayList(taskdefs.asJava),
        ArrayList(testRunners.asJava),
      )
      testListeners.foreach(_.doInit())
      val result =
        val ct = WorkerConnection.Tcp
        val w = WorkerExchange.startWorker(fork, if useClassLoader then Nil else cpFiles, ct)
        val wl = React(randomId, log, opts.testListeners, resultsAcc, w.process)
        try
          WorkerExchange.registerListener(wl)
          val paramJson = g.toJson(param, param.getClass)
          val json = jsonRpcRequest(randomId, "test", paramJson)
          w.println(json)
          if wl.blockForResponse() != 0 then
            throw MessageOnlyException("Forked test harness failed")
          testOutputResult
        finally WorkerExchange.unregisterListener(wl)
      testListeners.foreach(_.doComplete(result.overall))
      result
    } // end task

  private def jsonRpcRequest(id: Long, method: String, params: String): String =
    s"""{ "jsonrpc": "2.0", "method": "$method", "params": $params, "id": $id }"""

  private def forkFingerprint(f: Fingerprint): Fingerprint & Serializable =
    f match
      case s: SubclassFingerprint  => ForkTestMain.SubclassFingerscan(s)
      case a: AnnotatedFingerprint => ForkTestMain.AnnotatedFingerscan(a)
      case _                       => sys.error("Unknown fingerprint type: " + f.getClass)
end ForkTests

private class React(
    id: Long,
    log: Logger,
    listeners: Seq[TestReportListener],
    results: mutable.Map[String, SuiteResult],
    process: Process
) extends WorkerResponseListener:
  val g = WorkerMain.mkGson()
  val promise: Promise[Int] = Promise()

  /** Events per test group, accumulated for [[SuiteResult]] (listeners get each event immediately). */
  private val progressEvents = mutable.Map.empty[String, mutable.ArrayBuffer[testing.Event]]
  override def apply(line: String): Unit =
    try
      val o = JsonParser.parseString(line).getAsJsonObject()
      if o.has("id") then
        val resId = o.getAsJsonPrimitive("id").getAsLong()
        if resId == id then
          if promise.isCompleted then ()
          else if o.has("error") then promise.failure(new RuntimeException(line))
          else promise.success(0)
        else ()
      // per JSON-PRC notifications do not have "id" field, so we use "re"
      else if o.has("re") && o.has("method") then
        val resId = o.getAsJsonPrimitive("re").getAsLong()
        if resId == id then processNotification(o)
        else ()
      else ()
    catch
      case _: JsonSyntaxException => log.info(line)
      case NonFatal(_)            => ()

  override def notifyExit(p: Process): Unit =
    if !process.isAlive() && !promise.isCompleted then
      val exitCode = process.exitValue()
      if exitCode != 0 then
        promise.failure(new RuntimeException(s"Forked test process exited with code $exitCode"))
      else promise.success(exitCode)

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
          listeners.foreach(_.startGroup(info.group))
        else ()
      case "testProgress" =>
        val params = o.getAsJsonObject("params")
        val info =
          g.fromJson[ForkTestMain.ForkEventsInfo](params, classOf[ForkTestMain.ForkEventsInfo])
        if info.id == id then
          val buf = progressEvents.getOrElseUpdate(info.group, mutable.ArrayBuffer.empty)
          for e <- info.events.asScala do
            buf += e
            listeners.foreach(_.testEvent(TestEvent(Seq(e))))
        else ()
      case "endTestGroup" =>
        val params = o.getAsJsonObject("params")
        val info =
          g.fromJson[ForkTestMain.ForkGroupEnd](params, classOf[ForkTestMain.ForkGroupEnd])
        if info.id == id then
          val events = progressEvents.remove(info.group).getOrElse(mutable.ArrayBuffer.empty).toSeq
          val suiteResult = SuiteResult(events)
          results += info.group -> suiteResult
          listeners.foreach(_.endGroup(info.group, suiteResult.result))
        else ()
      case "forkError" =>
        val params = o.getAsJsonObject("params")
        val info =
          g.fromJson[ForkTestMain.ForkErrorInfo](params, classOf[ForkTestMain.ForkErrorInfo])
        if info.id == id then
          log.trace(info.error)
          promise.failure(info.error)
        else ()
      case _ => ()

  def blockForResponse(): Int =
    Await.result(promise.future, Duration.Inf)
end React
