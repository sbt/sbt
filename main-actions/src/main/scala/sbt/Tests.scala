/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import std._
import xsbt.api.{ Discovered, Discovery }
import sbt.internal.inc.Analysis
import TaskExtra._
import sbt.internal.util.FeedbackProvidedException
import xsbti.api.Definition
import xsbti.compile.CompileAnalysis
import ConcurrentRestrictions.Tag

import testing.{
  AnnotatedFingerprint,
  Fingerprint,
  Framework,
  SubclassFingerprint,
  Runner,
  TaskDef,
  SuiteSelector,
  Task => TestTask
}
import scala.annotation.tailrec

import sbt.internal.util.ManagedLogger
import sbt.util.Logger
import sbt.protocol.testing.TestResult

sealed trait TestOption
object Tests {

  /**
   * The result of a test run.
   *
   * @param overall The overall result of execution across all tests for all test frameworks in this test run.
   * @param events The result of each test group (suite) executed during this test run.
   * @param summaries Explicit summaries directly provided by test frameworks.  This may be empty, in which case a default summary will be generated.
   */
  final case class Output(overall: TestResult,
                          events: Map[String, SuiteResult],
                          summaries: Iterable[Summary])

  /**
   * Summarizes a test run.
   *
   * @param name The name of the test framework providing this summary.
   * @param summaryText The summary message for tests run by the test framework.
   */
  final case class Summary(name: String, summaryText: String)

  /**
   * Defines a TestOption that will evaluate `setup` before any tests execute.
   * The ClassLoader provided to `setup` is the loader containing the test classes that will be run.
   * Setup is not currently performed for forked tests.
   */
  final case class Setup(setup: ClassLoader => Unit) extends TestOption

  /**
   * Defines a TestOption that will evaluate `setup` before any tests execute.
   * Setup is not currently performed for forked tests.
   */
  def Setup(setup: () => Unit) = new Setup(_ => setup())

  /**
   * Defines a TestOption that will evaluate `cleanup` after all tests execute.
   * The ClassLoader provided to `cleanup` is the loader containing the test classes that ran.
   * Cleanup is not currently performed for forked tests.
   */
  final case class Cleanup(cleanup: ClassLoader => Unit) extends TestOption

  /**
   * Defines a TestOption that will evaluate `cleanup` after all tests execute.
   * Cleanup is not currently performed for forked tests.
   */
  def Cleanup(cleanup: () => Unit) = new Cleanup(_ => cleanup())

  /** The names of tests to explicitly exclude from execution. */
  final case class Exclude(tests: Iterable[String]) extends TestOption

  final case class Listeners(listeners: Iterable[TestReportListener]) extends TestOption

  /** Selects tests by name to run.  Only tests for which `filterTest` returns true will be run. */
  final case class Filter(filterTest: String => Boolean) extends TestOption

  /** Test execution will be ordered by the position of the matching filter. */
  final case class Filters(filterTest: Seq[String => Boolean]) extends TestOption

  /** Defines a TestOption that passes arguments `args` to all test frameworks. */
  def Argument(args: String*): Argument = Argument(None, args.toList)

  /** Defines a TestOption that passes arguments `args` to only the test framework `tf`. */
  def Argument(tf: TestFramework, args: String*): Argument = Argument(Some(tf), args.toList)

  /**
   * Defines arguments to pass to test frameworks.
   *
   * @param framework The test framework the arguments apply to if one is specified in Some.
   *                  If None, the arguments will apply to all test frameworks.
   * @param args The list of arguments to pass to the selected framework(s).
   */
  final case class Argument(framework: Option[TestFramework], args: List[String]) extends TestOption

  /**
   * Configures test execution.
   *
   * @param options The options to apply to this execution, including test framework arguments, filters,
   *                and setup and cleanup work.
   * @param parallel If true, execute each unit of work returned by the test frameworks in separate sbt.Tasks.
   *                 If false, execute all work in a single sbt.Task.
   * @param tags The tags that should be added to each test task.  These can be used to apply restrictions on
   *             concurrent execution.
   */
  final case class Execution(options: Seq[TestOption], parallel: Boolean, tags: Seq[(Tag, Int)])

  /** Configures whether a group of tests runs in the same JVM or are forked. */
  sealed trait TestRunPolicy

  /** Configures a group of tests to run in the same JVM. */
  case object InProcess extends TestRunPolicy

  /** Configures a group of tests to be forked in a new JVM with forking options specified by `config`. */
  final case class SubProcess(config: ForkOptions) extends TestRunPolicy

  /** A named group of tests configured to run in the same JVM or be forked. */
  final case class Group(name: String, tests: Seq[TestDefinition], runPolicy: TestRunPolicy)

  private[sbt] final class ProcessedOptions(
      val tests: Vector[TestDefinition],
      val setup: Vector[ClassLoader => Unit],
      val cleanup: Vector[ClassLoader => Unit],
      val testListeners: Vector[TestReportListener]
  )
  private[sbt] def processOptions(config: Execution,
                                  discovered: Vector[TestDefinition],
                                  log: Logger): ProcessedOptions = {
    import collection.mutable.{ HashSet, ListBuffer }
    val testFilters = new ListBuffer[String => Boolean]
    var orderedFilters = Seq[String => Boolean]()
    val excludeTestsSet = new HashSet[String]
    val setup, cleanup = new ListBuffer[ClassLoader => Unit]
    val testListeners = new ListBuffer[TestReportListener]
    val undefinedFrameworks = new ListBuffer[String]

    for (option <- config.options) {
      option match {
        case Filter(include) => testFilters += include
        case Filters(includes) =>
          if (orderedFilters.nonEmpty) sys.error("Cannot define multiple ordered test filters.")
          else orderedFilters = includes
        case Exclude(exclude)         => excludeTestsSet ++= exclude
        case Listeners(listeners)     => testListeners ++= listeners
        case Setup(setupFunction)     => setup += setupFunction
        case Cleanup(cleanupFunction) => cleanup += cleanupFunction
        case _: Argument              => // now handled by whatever constructs `runners`
      }
    }

    if (excludeTestsSet.nonEmpty)
      log.debug(excludeTestsSet.mkString("Excluding tests: \n\t", "\n\t", ""))
    if (undefinedFrameworks.nonEmpty)
      log.warn(
        "Arguments defined for test frameworks that are not present:\n\t" + undefinedFrameworks
          .mkString("\n\t"))

    def includeTest(test: TestDefinition) =
      !excludeTestsSet.contains(test.name) && testFilters.forall(filter => filter(test.name))
    val filtered0 = discovered.filter(includeTest).toList.distinct
    val tests =
      if (orderedFilters.isEmpty) filtered0
      else orderedFilters.flatMap(f => filtered0.filter(d => f(d.name))).toList.distinct
    val uniqueTests = distinctBy(tests)(_.name)
    new ProcessedOptions(uniqueTests.toVector,
                         setup.toVector,
                         cleanup.toVector,
                         testListeners.toVector)
  }

  private[this] def distinctBy[T, K](in: Seq[T])(f: T => K): Seq[T] = {
    val seen = new collection.mutable.HashSet[K]
    in.filter(t => seen.add(f(t)))
  }

  def apply(frameworks: Map[TestFramework, Framework],
            testLoader: ClassLoader,
            runners: Map[TestFramework, Runner],
            discovered: Vector[TestDefinition],
            config: Execution,
            log: ManagedLogger): Task[Output] = {
    val o = processOptions(config, discovered, log)
    testTask(testLoader,
             frameworks,
             runners,
             o.tests,
             o.setup,
             o.cleanup,
             log,
             o.testListeners,
             config)
  }

  def testTask(loader: ClassLoader,
               frameworks: Map[TestFramework, Framework],
               runners: Map[TestFramework, Runner],
               tests: Vector[TestDefinition],
               userSetup: Iterable[ClassLoader => Unit],
               userCleanup: Iterable[ClassLoader => Unit],
               log: ManagedLogger,
               testListeners: Vector[TestReportListener],
               config: Execution): Task[Output] = {
    def fj(actions: Iterable[() => Unit]): Task[Unit] = nop.dependsOn(actions.toSeq.fork(_()): _*)
    def partApp(actions: Iterable[ClassLoader => Unit]) = actions.toSeq map { a => () =>
      a(loader)
    }

    val (frameworkSetup, runnables, frameworkCleanup) =
      TestFramework.testTasks(frameworks, runners, loader, tests, log, testListeners)

    val setupTasks = fj(partApp(userSetup) :+ frameworkSetup)
    val mainTasks =
      if (config.parallel)
        makeParallel(loader, runnables, setupTasks, config.tags) //.toSeq.join
      else
        makeSerial(loader, runnables, setupTasks, config.tags)
    val taggedMainTasks = mainTasks.tagw(config.tags: _*)
    taggedMainTasks map processResults flatMap { results =>
      val cleanupTasks = fj(partApp(userCleanup) :+ frameworkCleanup(results.overall))
      cleanupTasks map { _ =>
        results
      }
    }
  }
  type TestRunnable = (String, TestFunction)

  private def createNestedRunnables(loader: ClassLoader,
                                    testFun: TestFunction,
                                    nestedTasks: Seq[TestTask]): Seq[(String, TestFunction)] =
    nestedTasks.view.zipWithIndex map {
      case (nt, idx) =>
        val testFunDef = testFun.taskDef
        (testFunDef.fullyQualifiedName,
         TestFramework.createTestFunction(loader,
                                          new TaskDef(testFunDef.fullyQualifiedName + "-" + idx,
                                                      testFunDef.fingerprint,
                                                      testFunDef.explicitlySpecified,
                                                      testFunDef.selectors),
                                          testFun.runner,
                                          nt))
    }

  def makeParallel(loader: ClassLoader,
                   runnables: Iterable[TestRunnable],
                   setupTasks: Task[Unit],
                   tags: Seq[(Tag, Int)]): Task[Map[String, SuiteResult]] =
    toTasks(loader, runnables.toSeq, tags).dependsOn(setupTasks)

  def toTasks(loader: ClassLoader,
              runnables: Seq[TestRunnable],
              tags: Seq[(Tag, Int)]): Task[Map[String, SuiteResult]] = {
    val tasks = runnables.map { case (name, test) => toTask(loader, name, test, tags) }
    tasks.join.map(_.foldLeft(Map.empty[String, SuiteResult]) {
      case (sum, e) =>
        val merged = sum.toSeq ++ e.toSeq
        val grouped = merged.groupBy(_._1)
        grouped.mapValues(_.map(_._2).foldLeft(SuiteResult.Empty) {
          case (resultSum, result) => resultSum + result
        })
    })
  }

  def toTask(loader: ClassLoader,
             name: String,
             fun: TestFunction,
             tags: Seq[(Tag, Int)]): Task[Map[String, SuiteResult]] = {
    val base = task { (name, fun.apply()) }
    val taggedBase = base.tagw(tags: _*).tag(fun.tags.map(ConcurrentRestrictions.Tag(_)): _*)
    taggedBase flatMap {
      case (name, (result, nested)) =>
        val nestedRunnables = createNestedRunnables(loader, fun, nested)
        toTasks(loader, nestedRunnables, tags).map { currentResultMap =>
          val newResult =
            currentResultMap.get(name) match {
              case Some(currentResult) => currentResult + result
              case None                => result
            }
          currentResultMap.updated(name, newResult)
        }
    }
  }

  def makeSerial(loader: ClassLoader,
                 runnables: Seq[TestRunnable],
                 setupTasks: Task[Unit],
                 tags: Seq[(Tag, Int)]): Task[List[(String, SuiteResult)]] = {
    @tailrec
    def processRunnable(runnableList: List[TestRunnable],
                        acc: List[(String, SuiteResult)]): List[(String, SuiteResult)] =
      runnableList match {
        case hd :: rst =>
          val testFun = hd._2
          val (result, nestedTasks) = testFun.apply()
          val nestedRunnables = createNestedRunnables(loader, testFun, nestedTasks)
          processRunnable(nestedRunnables.toList ::: rst, (hd._1, result) :: acc)
        case Nil => acc
      }

    task { processRunnable(runnables.toList, List.empty) } dependsOn (setupTasks)
  }

  def processResults(results: Iterable[(String, SuiteResult)]): Output =
    Output(overall(results.map(_._2.result)), results.toMap, Iterable.empty)

  private def severity(r: TestResult): Int =
    r match {
      case TestResult.Passed => 0
      case TestResult.Failed => 1
      case TestResult.Error  => 2
    }

  def foldTasks(results: Seq[Task[Output]], parallel: Boolean): Task[Output] =
    if (results.isEmpty)
      task { Output(TestResult.Passed, Map.empty, Nil) } else if (parallel)
      reduced(results.toIndexedSeq, {
        case (Output(v1, m1, _), Output(v2, m2, _)) =>
          Output(if (severity(v1) < severity(v2)) v2 else v1, m1 ++ m2, Iterable.empty)
      })
    else {
      def sequence(tasks: List[Task[Output]], acc: List[Output]): Task[List[Output]] =
        tasks match {
          case Nil => task(acc.reverse)
          case hd :: tl =>
            hd flatMap { out =>
              sequence(tl, out :: acc)
            }
        }
      sequence(results.toList, List()) map { ress =>
        val (rs, ms) = ress.unzip { e =>
          (e.overall, e.events)
        }
        Output(overall(rs), ms reduce (_ ++ _), Iterable.empty)
      }
    }
  def overall(results: Iterable[TestResult]): TestResult =
    ((TestResult.Passed: TestResult) /: results) { (acc, result) =>
      if (severity(acc) < severity(result)) result else acc
    }
  def discover(frameworks: Seq[Framework],
               analysis: CompileAnalysis,
               log: Logger): (Seq[TestDefinition], Set[String]) =
    discover(frameworks flatMap TestFramework.getFingerprints, allDefs(analysis), log)

  def allDefs(analysis: CompileAnalysis) = analysis match {
    case analysis: Analysis =>
      val acs: Seq[xsbti.api.AnalyzedClass] = analysis.apis.internal.values.toVector
      acs.flatMap { ac =>
        val companions = ac.api
        val all =
          Seq(companions.classApi, companions.objectApi) ++
            companions.classApi.structure.declared ++ companions.classApi.structure.inherited ++
            companions.objectApi.structure.declared ++ companions.objectApi.structure.inherited

        all
      }.toSeq
  }
  def discover(fingerprints: Seq[Fingerprint],
               definitions: Seq[Definition],
               log: Logger): (Seq[TestDefinition], Set[String]) = {
    val subclasses = fingerprints collect {
      case sub: SubclassFingerprint => (sub.superclassName, sub.isModule, sub)
    };
    val annotations = fingerprints collect {
      case ann: AnnotatedFingerprint => (ann.annotationName, ann.isModule, ann)
    };
    log.debug("Subclass fingerprints: " + subclasses)
    log.debug("Annotation fingerprints: " + annotations)

    def firsts[A, B, C](s: Seq[(A, B, C)]): Set[A] = s.map(_._1).toSet
    def defined(in: Seq[(String, Boolean, Fingerprint)],
                names: Set[String],
                IsModule: Boolean): Seq[Fingerprint] =
      in collect { case (name, IsModule, print) if names(name) => print }

    def toFingerprints(d: Discovered): Seq[Fingerprint] =
      defined(subclasses, d.baseClasses, d.isModule) ++
        defined(annotations, d.annotations, d.isModule)

    val discovered = Discovery(firsts(subclasses), firsts(annotations))(definitions)
    // TODO: To pass in correct explicitlySpecified and selectors
    val tests = for ((df, di) <- discovered; fingerprint <- toFingerprints(di))
      yield new TestDefinition(df.name, fingerprint, false, Array(new SuiteSelector))
    val mains = discovered collect { case (df, di) if di.hasMain => df.name }
    (tests, mains.toSet)
  }
}

final class TestsFailedException
    extends RuntimeException("Tests unsuccessful")
    with FeedbackProvidedException
