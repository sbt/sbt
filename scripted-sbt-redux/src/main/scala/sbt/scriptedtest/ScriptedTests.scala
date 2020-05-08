/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package scriptedtest

import java.io.{ FileNotFoundException, IOException }
import java.net.SocketException
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.ForkJoinPool

import sbt.internal.io.Resources
import sbt.internal.scripted._

import scala.collection.parallel.ForkJoinTaskSupport
import scala.collection.{ GenSeq, mutable }
import scala.util.Try
import scala.util.control.NonFatal

final class ScriptedTests(
    resourceBaseDirectory: File,
    bufferLog: Boolean,
    launchOpts: Seq[String],
) {
  import ScriptedTests.TestRunner

  private val testResources = new Resources(resourceBaseDirectory)

  val ScriptFilename = "test"
  val PendingScriptFilename = "pending"

  /** Returns a sequence of test runners that have to be applied in the call site. */
  def singleScriptedTest(
      group: String,
      name: String,
      prescripted: File => Unit,
      log: Logger,
      scalaVersion: String,
      sbtVersion: String,
      classpath: Seq[File]
  ): Seq[TestRunner] = {

    // Test group and names may be file filters (like '*')
    for {
      groupDir <- (resourceBaseDirectory * group).get
      nme <- (groupDir * name).get
      if !(nme.isFile)
    } yield {
      val g = groupDir.getName
      val n = nme.getName
      val label = s"$g / $n"
      () => {
        log.info(s"Running $label")
        val result = testResources.readWriteResourceDirectory(g, n) { testDirectory =>
          val buffer = new BufferedLogger(new FullLogger(log))
          val singleTestRunner = () => {
            val handlers =
              createScriptedHandlers(testDirectory, buffer, scalaVersion, sbtVersion, classpath)
            val runner = new BatchScriptRunner
            val states = new mutable.HashMap[StatementHandler, StatementHandler#State]()
            try commonRunTest(label, testDirectory, prescripted, handlers, runner, states, buffer)
            finally runner.close()
          }
          runOrHandleDisabled(label, testDirectory, singleTestRunner, buffer)
        }
        Seq(result)
      }
    }
  }

  private def createScriptedHandlers(
      testDir: File,
      buffered: Logger,
      scalaVersion: String,
      sbtVersion: String,
      classpath: Seq[File]
  ): Map[Char, StatementHandler] = {
    val fileHandler = new FileCommands(testDir)
    val remoteSbtCreator =
      new RunFromSourceBasedRemoteSbtCreator(
        testDir,
        buffered,
        launchOpts,
        scalaVersion,
        sbtVersion,
        classpath
      )
    val sbtHandler = new SbtHandler(remoteSbtCreator)
    Map('$' -> fileHandler, '>' -> sbtHandler, '#' -> CommentHandler)
  }

  /** Returns a sequence of test runners that have to be applied in the call site. */
  def batchScriptedRunner(
      testGroupAndNames: Seq[(String, String)],
      prescripted: File => Unit,
      sbtInstances: Int,
      log: Logger,
      scalaVersion: String,
      sbtVersion: String,
      classpath: Seq[File]
  ): Seq[TestRunner] = {
    // Test group and names may be file filters (like '*')
    val groupAndNameDirs = {
      for {
        (group, name) <- testGroupAndNames
        groupDir <- (resourceBaseDirectory * group).get
        testDir <- (groupDir * name).get
      } yield (groupDir, testDir)
    }

    type TestInfo = ((String, String), File)

    val labelsAndDirs = groupAndNameDirs.filterNot(_._2.isFile).map {
      case (groupDir, nameDir) =>
        val groupName = groupDir.getName
        val testName = nameDir.getName
        val testDirectory = testResources.readOnlyResourceDirectory(groupName, testName)
        (groupName, testName) -> testDirectory
    }

    if (labelsAndDirs.isEmpty) List()
    else {
      val totalSize = labelsAndDirs.size
      val batchSize = totalSize / sbtInstances match {
        case 0 => 1
        case s => s
      }

      val runFromSourceBasedTestsUnfiltered = labelsAndDirs
      val runFromSourceBasedTests = runFromSourceBasedTestsUnfiltered.filterNot(windowsExclude)

      def logTests(size: Int, how: String) =
        log.info(
          f"Running $size / $totalSize (${size * 100d / totalSize}%3.2f%%) scripted tests with $how"
        )
      logTests(runFromSourceBasedTests.size, "RunFromSourceMain")

      def createTestRunners(tests: Seq[TestInfo]): Seq[TestRunner] = {
        tests
          .grouped(batchSize)
          .map { batch => () =>
            IO.withTemporaryDirectory {
              runBatchedTests(batch, _, prescripted, log, scalaVersion, sbtVersion, classpath)
            }
          }
          .toList
      }

      createTestRunners(runFromSourceBasedTests)
    }
  }

  private[this] val windowsExclude: (((String, String), File)) => Boolean =
    if (scala.util.Properties.isWin) {
      case (testName, _) =>
        testName match {
          case ("classloader-cache", "jni") => true // no native lib is built for windows
          case ("classloader-cache", "snapshot") =>
            true // the test overwrites a jar that is being used which is verboten in windows
          case ("nio", "make-clone") => true // uses gcc which isn't set up on all systems
          case _                     => false
        }
    }
    else _ => false

  /** Defines an auto plugin that is injected to sbt between every scripted session.
   *
   * It sets the name of the local root project for those tests run in batch mode.
   *
   * This is necessary because the current design to run tests in batch mode forces
   * scripted tests to share one common sbt dir instead of each one having its own.
   *
   * Sbt extracts the local root project name from the directory name. So those
   * scripted tests that don't set the name for the root and whose test files check
   * information based on the name will fail.
   *
   * The reason why we set the name here and not via `set` is because some tests
   * dump the session to check that their settings have been correctly applied.
   *
   * @param testName The test name used to extract the root project name.
   * @return A string-based implementation to run between every reload.
   */
  private def createAutoPlugin(testName: String) =
    s"""
      |import sbt._, Keys._
      |object InstrumentScripted extends AutoPlugin {
      |  override def trigger = allRequirements
      |  override def globalSettings: Seq[Setting[_]] =
      |    Seq(commands += setUpScripted) ++ super.globalSettings
      |
      |  def setUpScripted = Command.command("setUpScripted") { (state0: State) =>
      |    val nameScriptedSetting = name.in(LocalRootProject).:=(
      |        if (name.value.startsWith("sbt_")) "$testName" else name.value)
      |    val state1 = Project.extract(state0).appendWithoutSession(nameScriptedSetting, state0)
      |    "initialize" :: state1
      |  }
      |}
    """.stripMargin

  /** Defines the batch execution of scripted tests.
   *
   * Scripted tests are run one after the other one recycling the handlers, under
   * the assumption that handlers do not produce side effects that can change scripted
   * tests' behaviours.
   *
   * In batch mode, the test runner performs these operations between executions:
   *
   * 1. Delete previous test files in the common test directory.
   * 2. Copy over next test files to the common test directory.
   * 3. Reload the sbt handler.
   *
   * @param groupedTests The labels and directories of the tests to run.
   * @param tempTestDir The common test directory.
   * @param preHook The hook to run before scripted execution.
   * @param log The logger.
   */
  private def runBatchedTests(
      groupedTests: Seq[((String, String), File)],
      tempTestDir: File,
      preHook: File => Unit,
      log: Logger,
      scalaVersion: String,
      sbtVersion: String,
      classpath: Seq[File]
  ): Seq[Option[String]] = {

    val runner = new BatchScriptRunner
    val buffer = new BufferedLogger(new FullLogger(log))
    val handlers = createScriptedHandlers(tempTestDir, buffer, scalaVersion, sbtVersion, classpath)
    val states = new BatchScriptRunner.States
    val seqHandlers = handlers.values.toList
    runner.initStates(states, seqHandlers)

    object ModifiedRetry {
      private lazy val limit = {
        val defaultLimit = 10
        try System.getProperty("sbt.io.retry.limit", defaultLimit.toString).toInt
        catch { case NonFatal(_) => defaultLimit }
      }
      private[sbt] def apply[@specialized T](
          f: => T,
          excludedExceptions: Class[_ <: IOException]*
      ): T =
        apply(f, limit, excludedExceptions: _*)
      private[sbt] def apply[@specialized T](
          f: => T,
          limit: Int,
          excludedExceptions: Class[_ <: IOException]*
      ): T = {
        require(limit >= 1, "limit must be 1 or higher: was: " + limit)
        def filter(e: Exception): Boolean = excludedExceptions match {
          case s if s.nonEmpty =>
            !excludedExceptions.exists(_.isAssignableFrom(e.getClass))
          case _ =>
            true
        }
        var attempt = 1
        var firstException: IOException = null
        while (attempt <= limit) {
          try {
            return f
          } catch {
            case e: IOException if filter(e) =>
              if (firstException == null) firstException = e

              Thread.sleep(100);
              attempt += 1
          }
        }
        throw firstException
      }
    }

    def runBatchTests = {
      groupedTests.map {
        case ((group, name), originalDir) =>
          val label = s"$group/$name"
          log.info(s"Running $label")

          import java.nio.file.FileAlreadyExistsException
          ModifiedRetry(
            // Copy test's contents and reload the sbt instance to pick them up
            IO.copyDirectory(originalDir, tempTestDir),
            excludedExceptions = classOf[FileAlreadyExistsException]
          )

          val runTest = () => {
            // Reload and initialize (to reload contents of .sbtrc files)
            val pluginImplementation = createAutoPlugin(name)
            val pluginFile = tempTestDir / "project" / "InstrumentScripted.scala"
            IO.write(pluginFile, pluginImplementation)
            def sbtHandlerError = sys error "Missing sbt handler. Scripted is misconfigured."
            val sbtHandler = handlers.getOrElse('>', sbtHandlerError)
            val commandsToRun = ";reload;setUpScripted"
            val statement = Statement(commandsToRun, Nil, successExpected = true, line = -1)

            // Run reload inside the hook to reuse error handling for pending tests
            val wrapHook = (file: File) => {
              preHook(file)
              while (!Try(IO.read(pluginFile)).toOption.contains(pluginImplementation)) {
                IO.write(pluginFile, pluginImplementation)
              }
              try runner.processStatement(sbtHandler, statement, states)
              catch {
                case t: Throwable =>
                  val newMsg = "Reload for scripted batch execution failed."
                  throw new TestException(statement, newMsg, t)
              }
            }

            commonRunTest(label, tempTestDir, wrapHook, handlers, runner, states, buffer)
          }

          // Run the test and delete files (except global that holds local scala jars)
          val result = runOrHandleDisabled(label, tempTestDir, runTest, buffer)
          val view = sbt.nio.file.FileTreeView.default
          val base = tempTestDir.getCanonicalFile.toGlob
          val global = base / "global"
          val globalLogging = base / ** / "global-logging"
          def recursiveFilter(glob: Glob): PathFilter = (glob: PathFilter) || glob / **
          val keep: PathFilter = recursiveFilter(global) || recursiveFilter(globalLogging)
          val toDelete = view.list(base / **, !keep).map(_._1).sorted.reverse
          toDelete.foreach { p =>
            try Files.deleteIfExists(p)
            catch { case _: IOException => }
          }
          result
      }
    }

    try runBatchTests
    finally {
      runner.cleanUpHandlers(seqHandlers, states)
      runner.close()
    }
  }

  private def runOrHandleDisabled(
      label: String,
      testDirectory: File,
      runTest: () => Option[String],
      log: Logger
  ): Option[String] = {
    val existsDisabled = new File(testDirectory, "disabled").isFile
    if (!existsDisabled) runTest()
    else {
      log.info(s"D $label [DISABLED]")
      None
    }
  }

  private val PendingLabel = "[PENDING]"

  private def commonRunTest(
      label: String,
      testDirectory: File,
      preScriptedHook: File => Unit,
      handlers: Map[Char, StatementHandler],
      runner: BatchScriptRunner,
      states: BatchScriptRunner.States,
      log: BufferedLogger
  ): Option[String] = {
    if (bufferLog) log.record()

    val (file, pending) = {
      val normal = (new File(testDirectory, ScriptFilename), false)
      val normalScript = (new File(testDirectory, s"$ScriptFilename.script"), false)
      val pending = (new File(testDirectory, PendingScriptFilename), true)
      val pendingScript = (new File(testDirectory, s"$PendingScriptFilename.script"), true)

      List(pending, pendingScript, normal, normalScript).find(_._1.isFile) match {
        case Some(script) => script
        case None         => throw new FileNotFoundException("no test scripts found")
      }
    }

    val pendingMark: String = if (pending) PendingLabel else ""

    def testFailed(t: Throwable): Option[String] = {
      if (pending) log.clear() else log.stop()
      log.error(s"x $label $pendingMark")
      if (!NonFatal(t)) throw t // We make sure fatal errors are rethrown
      if (t.isInstanceOf[TestException]) {
        t.getCause match {
          case null | _: SocketException => log.error(s" Cause of test exception: ${t.getMessage}")
          case _                         => if (!pending) t.printStackTrace()
        }
        log.play()
      }
      if (pending) None else Some(label)
    }

    import scala.util.control.Exception.catching
    catching(classOf[TestException]).withApply(testFailed).andFinally(log.clear).apply {
      preScriptedHook(testDirectory)
      val parser = new TestScriptParser(handlers)
      val handlersAndStatements = parser.parse(file, stripQuotes = false)
      runner.apply(handlersAndStatements, states)

      // Handle successful tests
      log.info(s"+ $label $pendingMark")
      if (pending) {
        log.clear()
        log.error(" Pending test passed. Mark as passing to remove this failure.")
        Some(label)
      } else None
    }
  }
}

object ScriptedTests extends ScriptedRunner {

  /** Represents the function that runs the scripted tests, both in single or batch mode. */
  type TestRunner = () => Seq[Option[String]]

  def main(args: Array[String]): Unit = {
    val directory = new File(args(0))
    val buffer = args(1).toBoolean
    val sbtVersion = args(2)
    val defScalaVersion = args(3)
    //  val buildScalaVersions = args(4)
    //val bootProperties = new File(args(5))
    val tests = args.drop(6)
    val logger = TestConsoleLogger()
    val cp = System.getProperty("java.class.path", "").split(java.io.File.pathSeparator).map(file)
    runInParallel(
      directory,
      buffer,
      tests,
      logger,
      Array(),
      new java.util.ArrayList[File],
      defScalaVersion,
      sbtVersion,
      cp,
      1
    )
  }

}

/** Runner for `scripted`. Not be confused with ScriptRunner. */
class ScriptedRunner {
  // This is called by project/Scripted.scala
  // Using java.util.List[File] to encode File => Unit
  // This is used by sbt-scripted sbt 1.x
  def runInParallel(
      baseDir: File,
      bufferLog: Boolean,
      tests: Array[String],
      logger: Logger,
      launchOpts: Array[String],
      prescripted: java.util.List[File],
      scalaVersion: String,
      sbtVersion: String,
      classpath: Array[File],
      instances: Int
  ): Unit = {
    val addTestFile = (f: File) => { prescripted.add(f); () }
    val runner = new ScriptedTests(baseDir, bufferLog, launchOpts)
    val accept = isTestCompatible(baseDir, sbtVersion) _
    // The scripted tests mapped to the inputs that the user wrote after `scripted`.
    val scriptedTests =
      get(tests, baseDir, accept, logger).map(st => (st.group, st.name))
    val scriptedRunners =
      runner.batchScriptedRunner(
        scriptedTests,
        addTestFile,
        instances,
        logger,
        scalaVersion,
        sbtVersion,
        classpath
      )
    val parallelRunners = scriptedRunners.toParArray
    parallelRunners.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(instances))
    runAll(parallelRunners)
  }
  def runInParallel(
      baseDir: File,
      bufferLog: Boolean,
      tests: Array[String],
      launchOpts: Array[String],
      prescripted: java.util.List[File],
      sbtVersion: String,
      scalaVersion: String,
      classpath: Array[File],
      instances: Int
  ): Unit =
    runInParallel(
      baseDir,
      bufferLog,
      tests,
      TestConsoleLogger(),
      launchOpts,
      prescripted,
      sbtVersion,
      scalaVersion,
      classpath,
      instances
    )

  private def reportErrors(errors: GenSeq[String]): Unit =
    if (errors.nonEmpty) sys.error(errors.mkString("Failed tests:\n\t", "\n\t", "\n")) else ()

  def runAll(toRun: GenSeq[ScriptedTests.TestRunner]): Unit =
    reportErrors(toRun.flatMap(test => test.apply().flatten))

  @deprecated("No longer used", "1.1.0")
  def get(tests: Seq[String], baseDirectory: File, log: Logger): Seq[ScriptedTest] =
    get(tests, baseDirectory, _ => true, log)

  def get(
      tests: Seq[String],
      baseDirectory: File,
      accept: ScriptedTest => Boolean,
      log: Logger,
  ): Seq[ScriptedTest] =
    if (tests.isEmpty) listTests(baseDirectory, accept, log) else parseTests(tests)

  @deprecated("No longer used", "1.1.0")
  def listTests(baseDirectory: File, log: Logger): Seq[ScriptedTest] =
    listTests(baseDirectory, _ => true, log)

  def listTests(
      baseDirectory: File,
      accept: ScriptedTest => Boolean,
      log: Logger,
  ): Seq[ScriptedTest] =
    (new ListTests(baseDirectory, accept, log)).listTests

  def parseTests(in: Seq[String]): Seq[ScriptedTest] =
    for (testString <- in) yield {
      val Array(group, name) = testString.split("/").map(_.trim)
      ScriptedTest(group, name)
    }

  private def isTestCompatible(resourceBaseDirectory: File, sbtVersion: String)(
      test: ScriptedTest
  ): Boolean = {
    import sbt.internal.librarymanagement.cross.CrossVersionUtil.binarySbtVersion
    val buildProperties = new Properties()
    val testDir = new File(new File(resourceBaseDirectory, test.group), test.name)
    val buildPropertiesFile = new File(new File(testDir, "project"), "build.properties")

    IO.load(buildProperties, buildPropertiesFile)

    Option(buildProperties.getProperty("sbt.version")) match {
      case Some(version) => binarySbtVersion(version) == binarySbtVersion(sbtVersion)
      case None          => true
    }
  }

}

final case class ScriptedTest(group: String, name: String) {
  override def toString = s"$group/$name"
}

private[sbt] final class ListTests(
    baseDirectory: File,
    accept: ScriptedTest => Boolean,
    log: Logger,
) {

  def filter = DirectoryFilter -- HiddenFileFilter

  def listTests: Seq[ScriptedTest] = {
    IO.listFiles(baseDirectory, filter) flatMap { group =>
      val groupName = group.getName
      listTests(group).map(ScriptedTest(groupName, _))
    }
  }

  private[this] def listTests(group: File): Set[String] = {
    val groupName = group.getName
    val allTests = IO.listFiles(group, filter)
    if (allTests.isEmpty) {
      log.warn(s"No tests in test group $groupName")
      Set.empty
    } else {
      val (included, skipped) =
        allTests.toList.partition(test => accept(ScriptedTest(groupName, test.getName)))
      if (included.isEmpty)
        log.warn(s"Test group $groupName skipped.")
      else if (skipped.nonEmpty) {
        log.warn(s"Tests skipped in group $groupName:")
        skipped.foreach(testName => log.warn(s" ${testName.getName}"))
      }
      Set(included.map(_.getName): _*)
    }
  }
}

class PendingTestSuccessException(label: String) extends Exception {
  override def getMessage: String =
    s"The pending test $label succeeded. Mark this test as passing to remove this failure."
}

private[sbt] object TestConsoleLogger {
  def apply() = new TestConsoleLogger()
}

// Remove dependencies to log4j to avoid mixup.
private[sbt] class TestConsoleLogger extends AbstractLogger {
  val out = ConsoleOut.systemOut
  def trace(t: => Throwable): Unit = {
    out.println(t.toString)
    // out.flush()
  }
  def success(message: => String): Unit = {
    out.println(message)
    // out.flush()
  }
  def log(level: Level.Value, message: => String): Unit = {
    out.println(s"[$level] $message")
    // out.flush()
  }
  def control(event: sbt.util.ControlEvent.Value, message: => String): Unit = ()
  def getLevel: sbt.util.Level.Value = Level.Info
  def getTrace: Int = Int.MaxValue
  def logAll(events: Seq[sbt.util.LogEvent]): Unit = ()
  def setLevel(newLevel: sbt.util.Level.Value): Unit = ()
  def setSuccessEnabled(flag: Boolean): Unit = ()
  def setTrace(flag: Int): Unit = ()
  def successEnabled: Boolean = true
}
