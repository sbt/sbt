/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */

package sbt
package test

import java.io.File
import java.nio.charset.Charset

import xsbt.IPC
import xsbt.test.{ CommentHandler, FileCommands, ScriptRunner, TestScriptParser }
import IO.wrapNull

final class ScriptedTests(resourceBaseDirectory: File, bufferLog: Boolean, launcher: File, launchOpts: Seq[String]) {
  import ScriptedTests._
  private val testResources = new Resources(resourceBaseDirectory)

  val ScriptFilename = "test"
  val PendingScriptFilename = "pending"

  def scriptedTest(group: String, name: String, log: xsbti.Logger): Seq[() => Option[String]] =
    scriptedTest(group, name, Logger.xlog2Log(log))
  def scriptedTest(group: String, name: String, log: Logger): Seq[() => Option[String]] =
    scriptedTest(group, name, emptyCallback, log)
  def scriptedTest(group: String, name: String, prescripted: File => Unit, log: Logger): Seq[() => Option[String]] = {
    import Path._
    import GlobFilter._
    var failed = false
    for (groupDir <- (resourceBaseDirectory * group).get; nme <- (groupDir * name).get) yield {
      val g = groupDir.getName
      val n = nme.getName
      val str = s"$g / $n"
      () => {
        println("Running " + str)
        testResources.readWriteResourceDirectory(g, n) { testDirectory =>
          val disabled = new File(testDirectory, "disabled").isFile
          if (disabled) {
            log.info("D " + str + " [DISABLED]")
            None
          } else {
            try { scriptedTest(str, testDirectory, prescripted, log); None }
            catch { case e: xsbt.test.TestException => Some(str) }
          }
        }
      }
    }
  }

  private def scriptedTest(label: String, testDirectory: File, prescripted: File => Unit, log: Logger): Unit =
    {
      val buffered = new BufferedLogger(new FullLogger(log))
      if (bufferLog)
        buffered.record()

      def createParser() =
        {
          val fileHandler = new FileCommands(testDirectory)
          val sbtHandler = new SbtHandler(testDirectory, launcher, buffered, launchOpts)
          new TestScriptParser(Map('$' -> fileHandler, '>' -> sbtHandler, '#' -> CommentHandler))
        }
      val (file, pending) = {
        val normal = new File(testDirectory, ScriptFilename)
        val pending = new File(testDirectory, PendingScriptFilename)
        if (pending.isFile) (pending, true) else (normal, false)
      }
      val pendingString = if (pending) " [PENDING]" else ""

      def runTest() =
        {
          val run = new ScriptRunner
          val parser = createParser()
          run(parser.parse(file))
        }
      def testFailed() {
        if (pending) buffered.clear() else buffered.stop()
        buffered.error("x " + label + pendingString)
      }

      try {
        prescripted(testDirectory)
        runTest()
        buffered.info("+ " + label + pendingString)
      } catch {
        case e: xsbt.test.TestException =>
          testFailed()
          e.getCause match {
            case null | _: java.net.SocketException => buffered.error("   " + e.getMessage)
            case _                                  => e.printStackTrace
          }
          if (!pending) throw e
        case e: Exception =>
          testFailed()
          if (!pending) throw e
      } finally { buffered.clear() }
    }
}

object ScriptedTests extends ScriptedRunner {
  val emptyCallback: File => Unit = { _ => () }
  def main(args: Array[String]) {
    val directory = new File(args(0))
    val buffer = args(1).toBoolean
    val sbtVersion = args(2)
    val defScalaVersion = args(3)
    val buildScalaVersions = args(4)
    val bootProperties = new File(args(5))
    val tests = args.drop(6)
    val logger = ConsoleLogger()
    run(directory, buffer, tests, logger, bootProperties, Array(), emptyCallback)
  }
}

class ScriptedRunner {
  import ScriptedTests._

  @deprecated("No longer used", "0.13.9")
  def run(resourceBaseDirectory: File, bufferLog: Boolean, tests: Array[String], bootProperties: File,
    launchOpts: Array[String]): Unit =
    run(resourceBaseDirectory, bufferLog, tests, ConsoleLogger(), bootProperties, launchOpts, emptyCallback) //new FullLogger(Logger.xlog2Log(log)))

  // This is called by project/Scripted.scala
  // Using java.util.List[File] to encode File => Unit
  def run(resourceBaseDirectory: File, bufferLog: Boolean, tests: Array[String], bootProperties: File,
    launchOpts: Array[String], prescripted: java.util.List[File]): Unit =
    run(resourceBaseDirectory, bufferLog, tests, ConsoleLogger(), bootProperties, launchOpts,
      { f: File => prescripted.add(f); () }) //new FullLogger(Logger.xlog2Log(log)))

  @deprecated("No longer used", "0.13.9")
  def run(resourceBaseDirectory: File, bufferLog: Boolean, tests: Array[String], bootProperties: File,
    launchOpts: Array[String], prescripted: File => Unit): Unit =
    run(resourceBaseDirectory, bufferLog, tests, ConsoleLogger(), bootProperties, launchOpts, prescripted)

  @deprecated("No longer used", "0.13.9")
  def run(resourceBaseDirectory: File, bufferLog: Boolean, tests: Array[String], logger: AbstractLogger, bootProperties: File,
    launchOpts: Array[String]): Unit =
    run(resourceBaseDirectory, bufferLog, tests, logger, bootProperties, launchOpts, emptyCallback)

  def run(resourceBaseDirectory: File, bufferLog: Boolean, tests: Array[String], logger: AbstractLogger, bootProperties: File,
    launchOpts: Array[String], prescripted: File => Unit) {
    val runner = new ScriptedTests(resourceBaseDirectory, bufferLog, bootProperties, launchOpts)
    val allTests = get(tests, resourceBaseDirectory, logger) flatMap {
      case ScriptedTest(group, name) =>
        runner.scriptedTest(group, name, prescripted, logger)
    }
    runAll(allTests)
  }
  def runAll(tests: Seq[() => Option[String]]) {
    val errors = for (test <- tests; err <- test()) yield err
    if (errors.nonEmpty)
      sys.error(errors.mkString("Failed tests:\n\t", "\n\t", "\n"))
  }
  def get(tests: Seq[String], baseDirectory: File, log: Logger): Seq[ScriptedTest] =
    if (tests.isEmpty) listTests(baseDirectory, log) else parseTests(tests)
  def listTests(baseDirectory: File, log: Logger): Seq[ScriptedTest] =
    (new ListTests(baseDirectory, _ => true, log)).listTests
  def parseTests(in: Seq[String]): Seq[ScriptedTest] =
    for (testString <- in) yield {
      val Array(group, name) = testString.split("/").map(_.trim)
      ScriptedTest(group, name)
    }
}

final case class ScriptedTest(group: String, name: String) extends NotNull {
  override def toString = group + "/" + name
}
private[test] object ListTests {
  def list(directory: File, filter: java.io.FileFilter) = wrapNull(directory.listFiles(filter))
}
import ListTests._
private[test] final class ListTests(baseDirectory: File, accept: ScriptedTest => Boolean, log: Logger) extends NotNull {
  def filter = DirectoryFilter -- HiddenFileFilter
  def listTests: Seq[ScriptedTest] =
    {
      list(baseDirectory, filter) flatMap { group =>
        val groupName = group.getName
        listTests(group).map(ScriptedTest(groupName, _))
      }
    }
  private[this] def listTests(group: File): Set[String] =
    {
      val groupName = group.getName
      val allTests = list(group, filter)
      if (allTests.isEmpty) {
        log.warn("No tests in test group " + groupName)
        Set.empty
      } else {
        val (included, skipped) = allTests.toList.partition(test => accept(ScriptedTest(groupName, test.getName)))
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

object CompatibilityLevel extends Enumeration {
  val Full, Basic, Minimal, Minimal27, Minimal28 = Value

  def defaultVersions(level: Value) =
    level match {
      case Full      => "2.7.4 2.7.7 2.9.0.RC1 2.8.0 2.8.1"
      case Basic     => "2.7.7 2.7.4 2.8.1 2.8.0"
      case Minimal   => "2.7.7 2.8.1"
      case Minimal27 => "2.7.7"
      case Minimal28 => "2.8.1"
    }
}
