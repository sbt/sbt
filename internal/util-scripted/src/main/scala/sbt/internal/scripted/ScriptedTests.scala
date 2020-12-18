/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package scripted

import java.io.File
import sbt.util.{ Logger, LoggerContext, Level }
import sbt.internal.util.{ Appender, ManagedLogger, ConsoleAppender, BufferedAppender }
import sbt.io.IO.wrapNull
import sbt.io.{ DirectoryFilter, HiddenFileFilter }
import sbt.io.syntax._
import sbt.internal.io.Resources
import java.util.concurrent.atomic.AtomicInteger

object ScriptedRunnerImpl {
  def run(
      resourceBaseDirectory: File,
      bufferLog: Boolean,
      tests: Array[String],
      handlersProvider: HandlersProvider
  ): Unit = {
    val context =
      LoggerContext(
        useLog4J = System.getProperty("sbt.log.uselog4j", "false") == "true",
        sbt.internal.util.Terminal.get
      )
    val runner = new ScriptedTests(resourceBaseDirectory, bufferLog, handlersProvider)
    val logger = newLogger(context)
    val allTests = get(tests, resourceBaseDirectory, logger) flatMap {
      case ScriptedTest(group, name) =>
        runner.scriptedTest(group, name, logger, context)
    }
    runAll(allTests)
  }
  def runAll(tests: Seq[() => Option[String]]): Unit = {
    val errors = for (test <- tests; err <- test()) yield err
    if (errors.nonEmpty)
      sys.error(errors.mkString("Failed tests:\n\t", "\n\t", "\n"))
  }
  def get(tests: Seq[String], baseDirectory: File, log: ManagedLogger): Seq[ScriptedTest] =
    if (tests.isEmpty) listTests(baseDirectory, log) else parseTests(tests)
  def listTests(baseDirectory: File, log: ManagedLogger): Seq[ScriptedTest] =
    (new ListTests(baseDirectory, _ => true, log)).listTests
  def parseTests(in: Seq[String]): Seq[ScriptedTest] =
    for (testString <- in) yield {
      val Array(group, name) = testString.split("/").map(_.trim)
      ScriptedTest(group, name)
    }
  private[sbt] val generateId: AtomicInteger = new AtomicInteger
  private[sbt] def newLogger(context: LoggerContext): ManagedLogger = {
    val loggerName = "scripted-" + generateId.incrementAndGet
    context.logger(loggerName, None, None)
  }
}

final class ScriptedTests(
    resourceBaseDirectory: File,
    bufferLog: Boolean,
    handlersProvider: HandlersProvider,
    stripQuotes: Boolean
) {
  def this(resourceBaseDirectory: File, bufferLog: Boolean, handlersProvider: HandlersProvider) =
    this(resourceBaseDirectory, bufferLog, handlersProvider, true)
  private val testResources = new Resources(resourceBaseDirectory)
  private val appender: Appender = ConsoleAppender()

  val ScriptFilename = "test"
  val PendingScriptFilename = "pending"

  def scriptedTest(group: String, name: String, log: xsbti.Logger): Seq[() => Option[String]] =
    scriptedTest(group, name, Logger.xlog2Log(log))

  @deprecated("Use scriptedTest that takes a LoggerContext", "1.4.0")
  def scriptedTest(
      group: String,
      name: String,
      log: ManagedLogger,
  ): Seq[() => Option[String]] =
    scriptedTest(group, name, (_ => ()), log, LoggerContext.globalContext)
  def scriptedTest(
      group: String,
      name: String,
      log: ManagedLogger,
      context: LoggerContext
  ): Seq[() => Option[String]] =
    scriptedTest(group, name, (_ => ()), log, context)

  @deprecated("Use scriptedTest that provides LoggerContext", "1.4.0")
  def scriptedTest(
      group: String,
      name: String,
      prescripted: File => Unit,
      log: ManagedLogger,
  ): Seq[() => Option[String]] =
    scriptedTest(group, name, prescripted, log, LoggerContext.globalContext)
  def scriptedTest(
      group: String,
      name: String,
      prescripted: File => Unit,
      log: ManagedLogger,
      context: LoggerContext,
  ): Seq[() => Option[String]] = {
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
            try {
              scriptedTest(str, testDirectory, prescripted, log, context); None
            } catch {
              case _: TestException | _: PendingTestSuccessException => Some(str)
            }
          }
        }
      }
    }
  }

  private def scriptedTest(
      label: String,
      testDirectory: File,
      prescripted: File => Unit,
      log: ManagedLogger,
      context: LoggerContext,
  ): Unit = {
    val buffered = BufferedAppender(appender)
    context.clearAppenders(log.name)
    context.addAppender(log.name, (buffered -> Level.Debug))
    if (bufferLog) {
      buffered.record()
    }
    def createParser() = {
      // val fileHandler = new FileCommands(testDirectory)
      // // val sbtHandler = new SbtHandler(testDirectory, launcher, buffered, launchOpts)
      // new TestScriptParser(Map('$' -> fileHandler, /* '>' -> sbtHandler, */ '#' -> CommentHandler))
      val scriptConfig = new ScriptConfig(label, testDirectory, log)
      new TestScriptParser(handlersProvider getHandlers scriptConfig)
    }
    val (file, pending) = {
      val normal = new File(testDirectory, ScriptFilename)
      val pending = new File(testDirectory, PendingScriptFilename)
      if (pending.isFile) (pending, true) else (normal, false)
    }
    val pendingString = if (pending) " [PENDING]" else ""

    def runTest(): Unit = {
      val run = new ScriptRunner
      val parser = createParser()
      run(parser.parse(file, stripQuotes))
    }
    def testFailed(): Unit = {
      if (pending) buffered.clearBuffer() else buffered.stopBuffer()
      log.error("x " + label + pendingString)
    }

    try {
      prescripted(testDirectory)
      runTest()
      log.info("+ " + label + pendingString)
      if (pending) throw new PendingTestSuccessException(label)
    } catch {
      case e: TestException =>
        testFailed()
        e.getCause match {
          case null | _: java.net.SocketException => log.error("   " + e.getMessage)
          case _                                  => if (!pending) e.printStackTrace
        }
        if (!pending) throw e
      case e: PendingTestSuccessException =>
        testFailed()
        log.error("  Mark as passing to remove this failure.")
        throw e
      case e: Exception =>
        testFailed()
        if (!pending) throw e
    } finally {
      buffered.clearBuffer()
    }
  }
}

// object ScriptedTests extends ScriptedRunner {
//   val emptyCallback: File => Unit = { _ => () }
// }

final case class ScriptedTest(group: String, name: String) {
  override def toString = group + "/" + name
}

object ListTests {
  def list(directory: File, filter: java.io.FileFilter) = wrapNull(directory.listFiles(filter))
}
import ListTests._
final class ListTests(baseDirectory: File, accept: ScriptedTest => Boolean, log: Logger) {
  def filter = DirectoryFilter -- HiddenFileFilter
  def listTests: Seq[ScriptedTest] = {
    list(baseDirectory, filter) flatMap { group =>
      val groupName = group.getName
      listTests(group).map(ScriptedTest(groupName, _))
    }
  }
  private[this] def listTests(group: File): Seq[String] = {
    val groupName = group.getName
    val allTests = list(group, filter).sortBy(_.getName)
    if (allTests.isEmpty) {
      log.warn("No tests in test group " + groupName)
      Seq.empty
    } else {
      val (included, skipped) =
        allTests.toList.partition(test => accept(ScriptedTest(groupName, test.getName)))
      if (included.isEmpty)
        log.warn("Test group " + groupName + " skipped.")
      else if (skipped.nonEmpty) {
        log.warn("Tests skipped in group " + group.getName + ":")
        skipped.foreach(testName => log.warn(" " + testName.getName))
      }
      Seq(included.map(_.getName): _*)
    }
  }
}

class PendingTestSuccessException(label: String) extends Exception {
  override def getMessage: String =
    s"The pending test $label succeeded. Mark this test as passing to remove this failure."
}
