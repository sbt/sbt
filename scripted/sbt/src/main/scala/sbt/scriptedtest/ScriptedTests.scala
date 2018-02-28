/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package scriptedtest

import java.io.File
import java.util.Properties

import scala.util.control.NonFatal
import sbt.internal.scripted._
import sbt.io.{ DirectoryFilter, HiddenFileFilter, IO }
import sbt.io.IO.wrapNull
import sbt.io.FileFilter._
import sbt.internal.io.Resources
import sbt.internal.util.{ BufferedLogger, ConsoleLogger, FullLogger }
import sbt.util.{ AbstractLogger, Logger }

import scala.collection.mutable
import scala.collection.parallel.ForkJoinTaskSupport
import scala.collection.parallel.mutable.ParSeq

final class ScriptedTests(resourceBaseDirectory: File,
                          bufferLog: Boolean,
                          launcher: File,
                          launchOpts: Seq[String]) {
  import sbt.io.syntax._
  import ScriptedTests._
  private val testResources = new Resources(resourceBaseDirectory)

  val ScriptFilename = "test"
  val PendingScriptFilename = "pending"

  def scriptedTest(group: String, name: String, log: xsbti.Logger): Seq[TestRunner] =
    scriptedTest(group, name, Logger.xlog2Log(log))
  def scriptedTest(group: String, name: String, log: Logger): Seq[TestRunner] =
    singleScriptedTest(group, name, emptyCallback, log)

  /** Returns a sequence of test runners that have to be applied in the call site. */
  def singleScriptedTest(group: String,
                         name: String,
                         prescripted: File => Unit,
                         log: Logger): Seq[TestRunner] = {

    // Test group and names may be file filters (like '*')
    for (groupDir <- (resourceBaseDirectory * group).get; nme <- (groupDir * name).get) yield {
      val g = groupDir.getName
      val n = nme.getName
      val label = s"$g / $n"
      () =>
        {
          println(s"Running $label")
          val result = testResources.readWriteResourceDirectory(g, n) { testDirectory =>
            val buffer = new BufferedLogger(new FullLogger(log))
            val singleTestRunner = () => {
              val handlers =
                createScriptedHandlers(testDirectory, buffer, RemoteSbtCreatorKind.LauncherBased)
              val runner = new BatchScriptRunner
              val states = new mutable.HashMap[StatementHandler, Any]()
              commonRunTest(label, testDirectory, prescripted, handlers, runner, states, buffer)
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
      remoteSbtCreatorKind: RemoteSbtCreatorKind,
  ): Map[Char, StatementHandler] = {
    val fileHandler = new FileCommands(testDir)
    val remoteSbtCreator = remoteSbtCreatorKind match {
      case RemoteSbtCreatorKind.LauncherBased =>
        new LauncherBasedRemoteSbtCreator(testDir, launcher, buffered, launchOpts)
      case RemoteSbtCreatorKind.RunFromSourceBased =>
        new RunFromSourceBasedRemoteSbtCreator(testDir, buffered, launchOpts)
    }
    val sbtHandler = new SbtHandler(remoteSbtCreator)
    Map('$' -> fileHandler, '>' -> sbtHandler, '#' -> CommentHandler)
  }

  /** Returns a sequence of test runners that have to be applied in the call site. */
  def batchScriptedRunner(
      testGroupAndNames: Seq[(String, String)],
      prescripted: File => Unit,
      sbtInstances: Int,
      log: Logger
  ): Seq[TestRunner] = {
    // Test group and names may be file filters (like '*')
    val groupAndNameDirs = {
      for {
        (group, name) <- testGroupAndNames
        groupDir <- resourceBaseDirectory.*(group).get
        testDir <- groupDir.*(name).get
      } yield (groupDir, testDir)
    }

    type TestInfo = ((String, String), File)

    val labelsAndDirs = groupAndNameDirs.map {
      case (groupDir, nameDir) =>
        val groupName = groupDir.getName
        val testName = nameDir.getName
        val testDirectory = testResources.readOnlyResourceDirectory(groupName, testName)
        (groupName, testName) -> testDirectory
    }

    if (labelsAndDirs.isEmpty) List()
    else {
      val totalSize = labelsAndDirs.size
      val batchSize = totalSize / sbtInstances

      val (launcherBasedTests, runFromSourceBasedTests) = labelsAndDirs.partition {
        case (testName, _) =>
          determineRemoteSbtCreatorKind(testName) match {
            case RemoteSbtCreatorKind.LauncherBased      => true
            case RemoteSbtCreatorKind.RunFromSourceBased => false
          }
      }

      def logTests(size: Int, how: String) =
        log.info(
          f"Running $size / $totalSize (${size * 100D / totalSize}%3.2f%%) scripted tests with $how")
      logTests(runFromSourceBasedTests.size, "RunFromSourceMain")
      logTests(launcherBasedTests.size, "sbt/launcher")

      def createTestRunners(
          tests: Seq[TestInfo],
          remoteSbtCreatorKind: RemoteSbtCreatorKind,
      ): Seq[TestRunner] = {
        tests
          .grouped(batchSize)
          .map { batch => () =>
            IO.withTemporaryDirectory {
              runBatchedTests(batch, _, prescripted, remoteSbtCreatorKind, log)
            }
          }
          .toList
      }

      createTestRunners(runFromSourceBasedTests, RemoteSbtCreatorKind.RunFromSourceBased) ++
        createTestRunners(launcherBasedTests, RemoteSbtCreatorKind.LauncherBased)
    }
  }

  private def determineRemoteSbtCreatorKind(testName: (String, String)): RemoteSbtCreatorKind = {
    import RemoteSbtCreatorKind._
    val (group, name) = testName
    s"$group/$name" match {
      case "actions/add-alias"                                      => LauncherBased // sbt/Package$
      case "actions/cross-multiproject"                             => LauncherBased // tbd
      case "actions/external-doc"                                   => LauncherBased // sbt/Package$
      case "actions/input-task"                                     => LauncherBased // sbt/Package$
      case "actions/input-task-dyn"                                 => LauncherBased // sbt/Package$
      case "compiler-project/run-test"                              => LauncherBased // sbt/Package$
      case "compiler-project/src-dep-plugin"                        => LauncherBased // sbt/Package$
      case "dependency-management/artifact"                         => LauncherBased // tbd
      case "dependency-management/cache-classifiers"                => LauncherBased // tbd
      case "dependency-management/cache-local"                      => LauncherBased // tbd
      case "dependency-management/cache-resolver"                   => LauncherBased // sbt/Package$
      case "dependency-management/cache-update"                     => LauncherBased // tbd
      case "dependency-management/cached-resolution-circular"       => LauncherBased // tbd
      case "dependency-management/cached-resolution-classifier"     => LauncherBased // tbd
      case "dependency-management/cached-resolution-configurations" => LauncherBased // tbd
      case "dependency-management/cached-resolution-conflicts"      => LauncherBased // tbd
      case "dependency-management/cached-resolution-exclude"        => LauncherBased // tbd
      case "dependency-management/cached-resolution-force"          => LauncherBased // tbd
      case "dependency-management/cached-resolution-interproj"      => LauncherBased // tbd
      case "dependency-management/cached-resolution-overrides"      => LauncherBased // tbd
      case "dependency-management/chainresolver"                    => LauncherBased // tbd
      case "dependency-management/circular-dependency"              => LauncherBased // tbd
      case "dependency-management/classifier"                       => LauncherBased // tbd
      case "dependency-management/default-resolvers"                => LauncherBased // tbd
      case "dependency-management/deliver-artifacts"                => LauncherBased // tbd
      case "dependency-management/exclude-transitive"               => LauncherBased // tbd
      case "dependency-management/extra"                            => LauncherBased // tbd
      case "dependency-management/force"                            => LauncherBased // tbd
      case "dependency-management/info"                             => LauncherBased // tbd
      case "dependency-management/inline-dependencies-a"            => LauncherBased // tbd
      case "dependency-management/ivy-settings-c"                   => LauncherBased // sbt/Package$
      case "dependency-management/latest-local-plugin"              => LauncherBased // sbt/Package$
      case "dependency-management/metadata-only-resolver"           => LauncherBased // tbd
      case "dependency-management/no-file-fails-publish"            => LauncherBased // tbd
      case "dependency-management/override"                         => LauncherBased // tbd
      case "dependency-management/parent-publish"                   => LauncherBased // sbt/Package$
      case "dependency-management/pom-parent-pom"                   => LauncherBased // tbd
      case "dependency-management/publish-to-maven-local-file"      => LauncherBased // sbt/Package$
      case "dependency-management/snapshot-resolution"              => LauncherBased // tbd
      case "dependency-management/test-artifact"                    => LauncherBased // sbt/Package$
      case "dependency-management/transitive-version-range"         => LauncherBased // tbd
      case "dependency-management/update-sbt-classifiers"           => LauncherBased // tbd
      case "dependency-management/url"                              => LauncherBased // tbd
      case "java/argfile"                                           => LauncherBased // sbt/Package$
      case "java/basic"                                             => LauncherBased // sbt/Package$
      case "java/varargs-main"                                      => LauncherBased // sbt/Package$
      case "package/lazy-name"                                      => LauncherBased // sbt/Package$
      case "package/manifest"                                       => LauncherBased // sbt/Package$
      case "package/resources"                                      => LauncherBased // sbt/Package$
      case "project/Class.forName"                                  => LauncherBased // sbt/Package$
      case "project/binary-plugin"                                  => LauncherBased // sbt/Package$
      case "project/default-settings"                               => LauncherBased // sbt/Package$
      case "project/extra"                                          => LauncherBased // tbd
      case "project/flatten"                                        => LauncherBased // sbt/Package$
      case "project/generated-root-no-publish"                      => LauncherBased // tbd
      case "project/lib"                                            => LauncherBased // sbt/Package$
      case "project/scripted-plugin"                                => LauncherBased // tbd
      case "project/scripted-skip-incompatible"                     => LauncherBased // sbt/Package$
      case "project/session-update-from-cmd"                        => LauncherBased // tbd
      case "project/transitive-plugins"                             => LauncherBased // tbd
      case "run/awt"                                                => LauncherBased // sbt/Package$
      case "run/classpath"                                          => LauncherBased // sbt/Package$
      case "run/daemon"                                             => LauncherBased // sbt/Package$
      case "run/daemon-exit"                                        => LauncherBased // sbt/Package$
      case "run/error"                                              => LauncherBased // sbt/Package$
      case "run/fork"                                               => LauncherBased // sbt/Package$
      case "run/fork-loader"                                        => LauncherBased // sbt/Package$
      case "run/non-local-main"                                     => LauncherBased // sbt/Package$
      case "run/spawn"                                              => LauncherBased // sbt/Package$
      case "run/spawn-exit"                                         => LauncherBased // sbt/Package$
      case "source-dependencies/binary"                             => LauncherBased // sbt/Package$
      case "source-dependencies/export-jars"                        => LauncherBased // sbt/Package$
      case "source-dependencies/implicit-search"                    => LauncherBased // sbt/Package$
      case "source-dependencies/java-basic"                         => LauncherBased // sbt/Package$
      case "source-dependencies/less-inter-inv"                     => LauncherBased // sbt/Package$
      case "source-dependencies/less-inter-inv-java"                => LauncherBased // sbt/Package$
      case "source-dependencies/linearization"                      => LauncherBased // sbt/Package$
      case "source-dependencies/named"                              => LauncherBased // sbt/Package$
      case "source-dependencies/specialized"                        => LauncherBased // sbt/Package$
      case _                                                        => RunFromSourceBased
    }
    // sbt/Package$ means:
    //   java.lang.NoClassDefFoundError: sbt/Package$ (wrong name: sbt/package$)
    // Typically from Compile / packageBin / packageOptions
  }

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
      |    val state1 = Project.extract(state0).append(nameScriptedSetting, state0)
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
      remoteSbtCreatorKind: RemoteSbtCreatorKind,
      log: Logger,
  ): Seq[Option[String]] = {

    val runner = new BatchScriptRunner
    val buffer = new BufferedLogger(new FullLogger(log))
    val handlers = createScriptedHandlers(tempTestDir, buffer, remoteSbtCreatorKind)
    val states = new BatchScriptRunner.States
    val seqHandlers = handlers.values.toList
    runner.initStates(states, seqHandlers)

    def runBatchTests = {
      groupedTests.map {
        case ((group, name), originalDir) =>
          val label = s"$group / $name"
          println(s"Running $label")
          // Copy test's contents and reload the sbt instance to pick them up
          IO.copyDirectory(originalDir, tempTestDir)

          val runTest = () => {
            // Reload and initialize (to reload contents of .sbtrc files)
            val pluginImplementation = createAutoPlugin(name)
            IO.write(tempTestDir / "project" / "InstrumentScripted.scala", pluginImplementation)
            val sbtHandlerError = "Missing sbt handler. Scripted is misconfigured."
            val sbtHandler = handlers.getOrElse('>', sbtHandlerError).asInstanceOf[SbtHandler]
            val commandsToRun = ";reload;setUpScripted"
            val statement = Statement(commandsToRun, Nil, successExpected = true, line = -1)

            // Run reload inside the hook to reuse error handling for pending tests
            val wrapHook = (file: File) => {
              preHook(file)
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
          IO.delete(tempTestDir.*("*" -- "global").get)
          result
      }
    }

    try runBatchTests
    finally runner.cleanUpHandlers(seqHandlers, states)
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
      createHandlers: Map[Char, StatementHandler],
      runner: BatchScriptRunner,
      states: BatchScriptRunner.States,
      log: BufferedLogger
  ): Option[String] = {
    if (bufferLog) log.record()

    val (file, pending) = {
      val normal = new File(testDirectory, ScriptFilename)
      val pending = new File(testDirectory, PendingScriptFilename)
      if (pending.isFile) (pending, true) else (normal, false)
    }

    val pendingMark = if (pending) PendingLabel else ""
    def testFailed(t: Throwable): Option[String] = {
      if (pending) log.clear() else log.stop()
      log.error(s"x $label $pendingMark")
      if (!NonFatal(t)) throw t // We make sure fatal errors are rethrown
      if (t.isInstanceOf[TestException]) {
        t.getCause match {
          case null | _: java.net.SocketException =>
            log.error(" Cause of test exception: " + t.getMessage)
          case _ => t.printStackTrace()
        }
      }
      if (pending) None else Some(label)
    }

    import scala.util.control.Exception.catching
    catching(classOf[TestException]).withApply(testFailed).andFinally(log.clear).apply {
      preScriptedHook(testDirectory)
      val handlers = createHandlers
      val parser = new TestScriptParser(handlers)
      val handlersAndStatements = parser.parse(file)
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
          prescripted: java.util.List[File]): Unit = {

    // Force Log4J to not use a thread context classloader otherwise it throws a CCE
    sys.props(org.apache.logging.log4j.util.LoaderUtil.IGNORE_TCCL_PROPERTY) = "true"

    run(resourceBaseDirectory, bufferLog, tests, ConsoleLogger(), bootProperties, launchOpts, {
      f: File =>
        prescripted.add(f); ()
    }) //new FullLogger(Logger.xlog2Log(log)))
  }

  // This is called by sbt-scripted 0.13.x and 1.x (see https://github.com/sbt/sbt/issues/3245)
  def run(resourceBaseDirectory: File,
          bufferLog: Boolean,
          tests: Array[String],
          bootProperties: File,
          launchOpts: Array[String]): Unit =
    run(resourceBaseDirectory,
        bufferLog,
        tests,
        ConsoleLogger(),
        bootProperties,
        launchOpts,
        ScriptedTests.emptyCallback)

  def run(resourceBaseDirectory: File,
          bufferLog: Boolean,
          tests: Array[String],
          logger: AbstractLogger,
          bootProperties: File,
          launchOpts: Array[String],
          prescripted: File => Unit): Unit = {
    val runner = new ScriptedTests(resourceBaseDirectory, bufferLog, bootProperties, launchOpts)
    val sbtVersion = bootProperties.getName.dropWhile(!_.isDigit).dropRight(".jar".length)
    val accept = isTestCompatible(resourceBaseDirectory, sbtVersion) _
    val allTests = get(tests, resourceBaseDirectory, accept, logger) flatMap {
      case ScriptedTest(group, name) =>
        runner.singleScriptedTest(group, name, prescripted, logger)
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
                  addTestFile,
                  1)
  }

  // This is used by sbt-scripted sbt 1.x
  def runInParallel(
      baseDir: File,
      bufferLog: Boolean,
      tests: Array[String],
      bootProps: File,
      launchOpts: Array[String],
      prescripted: java.util.List[File],
      instances: Int
  ): Unit = {
    val logger = ConsoleLogger()
    val addTestFile = (f: File) => { prescripted.add(f); () }
    runInParallel(baseDir, bufferLog, tests, logger, bootProps, launchOpts, addTestFile, instances)
  }

  def runInParallel(
      resourceBaseDirectory: File,
      bufferLog: Boolean,
      tests: Array[String],
      logger: AbstractLogger,
      bootProperties: File,
      launchOpts: Array[String],
      prescripted: File => Unit,
      instances: Int
  ): Unit = {
    val runner = new ScriptedTests(resourceBaseDirectory, bufferLog, bootProperties, launchOpts)
    val sbtVersion = bootProperties.getName.dropWhile(!_.isDigit).dropRight(".jar".length)
    val accept = isTestCompatible(resourceBaseDirectory, sbtVersion) _
    // The scripted tests mapped to the inputs that the user wrote after `scripted`.
    val scriptedTests =
      get(tests, resourceBaseDirectory, accept, logger).map(st => (st.group, st.name))
    val scriptedRunners = runner.batchScriptedRunner(scriptedTests, prescripted, instances, logger)
    val parallelRunners = scriptedRunners.toParArray
    val pool = new java.util.concurrent.ForkJoinPool(instances)
    parallelRunners.tasksupport = new ForkJoinTaskSupport(pool)
    runAllInParallel(parallelRunners)
  }

  private def reportErrors(errors: Seq[String]): Unit =
    if (errors.nonEmpty) sys.error(errors.mkString("Failed tests:\n\t", "\n\t", "\n")) else ()

  def runAll(toRun: Seq[ScriptedTests.TestRunner]): Unit =
    reportErrors(toRun.flatMap(test => test.apply().flatten.toSeq))

  // We cannot reuse `runAll` because parallel collections != collections
  def runAllInParallel(tests: ParSeq[ScriptedTests.TestRunner]): Unit = {
    reportErrors(tests.flatMap(test => test.apply().flatten.toSeq).toList)
  }

  @deprecated("No longer used", "1.1.0")
  def get(tests: Seq[String], baseDirectory: File, log: Logger): Seq[ScriptedTest] =
    get(tests, baseDirectory, _ => true, log)
  def get(tests: Seq[String],
          baseDirectory: File,
          accept: ScriptedTest => Boolean,
          log: Logger): Seq[ScriptedTest] =
    if (tests.isEmpty) listTests(baseDirectory, accept, log) else parseTests(tests)

  @deprecated("No longer used", "1.1.0")
  def listTests(baseDirectory: File, log: Logger): Seq[ScriptedTest] =
    listTests(baseDirectory, _ => true, log)
  def listTests(baseDirectory: File,
                accept: ScriptedTest => Boolean,
                log: Logger): Seq[ScriptedTest] =
    (new ListTests(baseDirectory, accept, log)).listTests

  def parseTests(in: Seq[String]): Seq[ScriptedTest] =
    for (testString <- in) yield {
      val Array(group, name) = testString.split("/").map(_.trim)
      ScriptedTest(group, name)
    }

  private def isTestCompatible(resourceBaseDirectory: File, sbtVersion: String)(
      test: ScriptedTest): Boolean = {
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
  override def toString = group + "/" + name
}
private[sbt] object ListTests {
  def list(directory: File, filter: java.io.FileFilter) = wrapNull(directory.listFiles(filter))
}
import ListTests._
private[sbt] final class ListTests(baseDirectory: File,
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
