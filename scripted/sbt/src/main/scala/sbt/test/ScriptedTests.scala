/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */

package sbt
package test

import java.io.File

import scala.util.control.NonFatal
import sbt.internal.scripted.{
  CommentHandler,
  FileCommands,
  ScriptRunner,
  TestException,
  TestScriptParser
}
import sbt.io.{ DirectoryFilter, HiddenFileFilter }
import sbt.io.IO.wrapNull
import sbt.internal.io.Resources
import sbt.internal.util.{ BufferedLogger, ConsoleLogger, FullLogger }
import sbt.util.{ AbstractLogger, Logger }

import scala.collection.parallel.mutable.ParSeq

final class ScriptedTests(resourceBaseDirectory: File,
                          bufferLog: Boolean,
                          launcher: File,
                          launchOpts: Seq[String]) {
  import ScriptedTests._
  private val testResources = new Resources(resourceBaseDirectory)

  val ScriptFilename = "test"
  val PendingScriptFilename = "pending"

  def scriptedTest(group: String, name: String, log: xsbti.Logger): Seq[() => Option[String]] =
    scriptedTest(group, name, Logger.xlog2Log(log))
  def scriptedTest(group: String, name: String, log: Logger): Seq[() => Option[String]] =
    scriptedTest(group, name, emptyCallback, log)

  /** Returns a sequence of test runners that have to be applied in the call site. */
  def scriptedTest(group: String,
                   name: String,
                   prescripted: File => Unit,
                   log: Logger): Seq[TestRunner] = {
    import sbt.io.syntax._
    for (groupDir <- (resourceBaseDirectory * group).get; nme <- (groupDir * name).get) yield {
      val g = groupDir.getName
      val n = nme.getName
      val testLabel = s"$g / $n"
      () =>
        {
          println("Running " + testLabel)
          testResources.readWriteResourceDirectory(g, n) { testDirectory =>
            val disabled = new File(testDirectory, "disabled").isFile
            if (disabled) {
              log.info("D " + testLabel + " [DISABLED]")
              None
            } else scriptedTest(testLabel, testDirectory, prescripted, log)
          }
        }
    }
  }

  private val PendingLabel = "[PENDING]"
  private def scriptedTest(label: String,
                           testDirectory: File,
                           preScriptedHook: File => Unit,
                           log: Logger): Option[String] = {
    val buffered = new BufferedLogger(new FullLogger(log))
    if (bufferLog) buffered.record()

    val (file, pending) = {
      val normal = new File(testDirectory, ScriptFilename)
      val pending = new File(testDirectory, PendingScriptFilename)
      if (pending.isFile) (pending, true) else (normal, false)
    }

    val pendingMark = if (pending) PendingLabel else ""
    def testFailed(t: Throwable): Option[String] = {
      if (pending) buffered.clear() else buffered.stop()
      buffered.error(s"x $label $pendingMark")
      if (!NonFatal(t)) throw t // We make sure fatal errors are rethrown
      if (t.isInstanceOf[TestException]) {
        t.getCause match {
          case null | _: java.net.SocketException =>
            buffered.error(" Cause of test exception: " + t.getMessage)
          case _ => t.printStackTrace()
        }
      }
      if (pending) None else Some(label)
    }

    import scala.util.control.Exception.catching
    catching(classOf[TestException]).withApply(testFailed).andFinally(buffered.clear).apply {
      preScriptedHook(testDirectory)
      val run = new ScriptRunner
      val fileHandler = new FileCommands(testDirectory)
      val sbtHandler = new SbtHandler(testDirectory, launcher, buffered, launchOpts)
      val handlers = Map('$' -> fileHandler, '>' -> sbtHandler, '#' -> CommentHandler)
      val parser = new TestScriptParser(handlers)
      run(parser.parse(file))

      // Handle successful tests
      buffered.info(s"+ $label $pendingMark")
      if (pending) {
        buffered.clear()
        buffered.error(" Pending test passed. Mark as passing to remove this failure.")
        Some(label)
      } else None
    }
  }
}

object ScriptedTests extends ScriptedRunner {

  /** Represents the function that runs the scripted tests. */
  type TestRunner = () => Option[String]

  val emptyCallback: File => Unit = _ => ()
  def main(args: Array[String]): Unit = {
    val directory = new File(args(0))
    val buffer = args(1).toBoolean
    //  val sbtVersion = args(2)
    //  val defScalaVersion = args(3)
    //  val buildScalaVersions = args(4)
    val bootProperties = new File(args(5))
    val tests = args.drop(6)
    val logger = ConsoleLogger()
    run(directory, buffer, tests, logger, bootProperties, Array(), emptyCallback)
  }
}

class ScriptedRunner {
  // This is called by project/Scripted.scala
  // Using java.util.List[File] to encode File => Unit
  def run(resourceBaseDirectory: File,
          bufferLog: Boolean,
          tests: Array[String],
          bootProperties: File,
          launchOpts: Array[String],
          prescripted: java.util.List[File]): Unit =
    run(resourceBaseDirectory, bufferLog, tests, ConsoleLogger(), bootProperties, launchOpts, {
      f: File =>
        prescripted.add(f); ()
    }) //new FullLogger(Logger.xlog2Log(log)))

  def run(resourceBaseDirectory: File,
          bufferLog: Boolean,
          tests: Array[String],
          logger: AbstractLogger,
          bootProperties: File,
          launchOpts: Array[String],
          prescripted: File => Unit): Unit = {
    val runner = new ScriptedTests(resourceBaseDirectory, bufferLog, bootProperties, launchOpts)
    val allTests = get(tests, resourceBaseDirectory, logger) flatMap {
      case ScriptedTest(group, name) =>
        runner.scriptedTest(group, name, prescripted, logger)
    }
    runAll(allTests)
  }

  def runInParallel(resourceBaseDirectory: File,
                    bufferLog: Boolean,
                    tests: Array[String],
                    bootProperties: File,
                    launchOpts: Array[String],
                    prescripted: java.util.List[File]): Unit = {
    val logger = ConsoleLogger()
    val addTestFile = (f: File) => { prescripted.add(f); () }
    runInParallel(resourceBaseDirectory,
                  bufferLog,
                  tests,
                  logger,
                  bootProperties,
                  launchOpts,
                  addTestFile)
  }

  def runInParallel(
      resourceBaseDirectory: File,
      bufferLog: Boolean,
      tests: Array[String],
      logger: AbstractLogger,
      bootProperties: File,
      launchOpts: Array[String],
      prescripted: File => Unit
  ): Unit = {
    val runner = new ScriptedTests(resourceBaseDirectory, bufferLog, bootProperties, launchOpts)
    val scriptedTests = get(tests, resourceBaseDirectory, logger)
    val scriptedTestRunners = scriptedTests
      .flatMap(t => runner.scriptedTest(t.group, t.name, prescripted, logger))
    runAllInParallel(scriptedTestRunners.toParArray)
  }

  private def reportErrors(errors: Seq[String]): Unit =
    if (errors.nonEmpty) sys.error(errors.mkString("Failed tests:\n\t", "\n\t", "\n")) else ()

  def runAll(tests: Seq[ScriptedTests.TestRunner]): Unit =
    reportErrors(tests.flatMap(test => test.apply().toSeq))

  // We cannot reuse `runAll` because parallel collections != collections
  def runAllInParallel(tests: ParSeq[ScriptedTests.TestRunner]): Unit =
    reportErrors(tests.flatMap(test => test.apply().toSeq).toList)

  def get(tests: Seq[String], baseDirectory: File, log: Logger): Seq[ScriptedTest] =
    if (tests.isEmpty) listTests(baseDirectory, log) else parseTests(tests)

  def listTests(baseDirectory: File, log: Logger): Seq[ScriptedTest] =
    new ListTests(baseDirectory, _ => true, log).listTests

  def parseTests(in: Seq[String]): Seq[ScriptedTest] =
    for (testString <- in) yield {
      val Array(group, name) = testString.split("/").map(_.trim)
      ScriptedTest(group, name)
    }
}

final case class ScriptedTest(group: String, name: String) {
  override def toString = group + "/" + name
}
private[test] object ListTests {
  def list(directory: File, filter: java.io.FileFilter) = wrapNull(directory.listFiles(filter))
}
import ListTests._
private[test] final class ListTests(baseDirectory: File,
                                    accept: ScriptedTest => Boolean,
                                    log: Logger) {
  def filter = DirectoryFilter -- HiddenFileFilter
  def listTests: Seq[ScriptedTest] = {
    list(baseDirectory, filter) flatMap { group =>
      val groupName = group.getName
      listTests(group).map(ScriptedTest(groupName, _))
    }
  }
  private[this] def listTests(group: File): Set[String] = {
    val groupName = group.getName
    val allTests = list(group, filter)
    if (allTests.isEmpty) {
      log.warn("No tests in test group " + groupName)
      Set.empty
    } else {
      val (included, skipped) =
        allTests.toList.partition(test => accept(ScriptedTest(groupName, test.getName)))
      if (included.isEmpty)
        log.warn("Test group " + groupName + " skipped.")
      else if (skipped.nonEmpty) {
        log.warn("Tests skipped in group " + group.getName + ":")
        skipped.foreach(testName => log.warn(" " + testName.getName))
      }
      Set(included.map(_.getName): _*)
    }
  }
}

class PendingTestSuccessException(label: String) extends Exception {
  override def getMessage: String =
    s"The pending test $label succeeded. Mark this test as passing to remove this failure."
}
