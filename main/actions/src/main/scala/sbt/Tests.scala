/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

	import std._
	import xsbt.api.{Discovered,Discovery}
	import inc.Analysis
	import TaskExtra._
	import Types._
	import xsbti.api.Definition
	import ConcurrentRestrictions.Tag

	import testing.{AnnotatedFingerprint, Fingerprint, Framework, SubclassFingerprint, Runner, TaskDef, SuiteSelector, Task => TestTask}
	import scala.annotation.tailrec

	import java.io.File

sealed trait TestOption
object Tests
{
	/** The result of a test run.
	*
	* @param overall The overall result of execution across all tests for all test frameworks in this test run.
	* @param events The result of each test group (suite) executed during this test run.
	* @param summaries Explicit summaries directly provided by test frameworks.  This may be empty, in which case a default summary will be generated.
	*/
	final case class Output(overall: TestResult.Value, events: Map[String,SuiteResult], summaries: Iterable[Summary])

	/** Summarizes a test run.
	*
	* @param name The name of the test framework providing this summary.
	* @param summaryText The summary message for tests run by the test framework.
	*/
	final case class Summary(name: String, summaryText: String)
	
	/** Defines a TestOption that will evaluate `setup` before any tests execute.
	* The ClassLoader provided to `setup` is the loader containing the test classes that will be run.
	* Setup is not currently performed for forked tests. */
	final case class Setup(setup: ClassLoader => Unit) extends TestOption

	/** Defines a TestOption that will evaluate `setup` before any tests execute.
	* Setup is not currently performed for forked tests. */
	def Setup(setup: () => Unit) = new Setup(_ => setup())

	/** Defines a TestOption that will evaluate `cleanup` after all tests execute.
	* The ClassLoader provided to `cleanup` is the loader containing the test classes that ran.
	* Cleanup is not currently performed for forked tests. */
	final case class Cleanup(cleanup: ClassLoader => Unit) extends TestOption

	/** Defines a TestOption that will evaluate `cleanup` after all tests execute.
	* Cleanup is not currently performed for forked tests. */
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

	/** Defines arguments to pass to test frameworks.
	*
	* @param framework The test framework the arguments apply to if one is specified in Some.
	*                  If None, the arguments will apply to all test frameworks.
	* @param args The list of arguments to pass to the selected framework(s).
	*/
	final case class Argument(framework: Option[TestFramework], args: List[String]) extends TestOption

	/** Configures test execution.
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
	object SubProcess {
		@deprecated("Construct SubProcess with a ForkOptions argument.", "0.13.0")
		def apply(javaOptions: Seq[String]): SubProcess = SubProcess(ForkOptions(runJVMOptions = javaOptions))
	}

	/** A named group of tests configured to run in the same JVM or be forked. */
	final case class Group(name: String, tests: Seq[TestDefinition], runPolicy: TestRunPolicy)

	private[sbt] final class ProcessedOptions(
		val tests: Seq[TestDefinition], 
		val setup: Seq[ClassLoader => Unit],
		val cleanup: Seq[ClassLoader => Unit],
		val testListeners: Seq[TestReportListener]
	)
	private[sbt] def processOptions(config: Execution, discovered: Seq[TestDefinition], log: Logger): ProcessedOptions =
	{
			import collection.mutable.{HashSet, ListBuffer, Map, Set}
		val testFilters = new ListBuffer[String => Boolean]
		var orderedFilters = Seq[String => Boolean]()
		val excludeTestsSet = new HashSet[String]
		val setup, cleanup = new ListBuffer[ClassLoader => Unit]
		val testListeners = new ListBuffer[TestReportListener]
		val undefinedFrameworks = new ListBuffer[String]

		for(option <- config.options)
		{
			option match
			{
				case Filter(include) => testFilters += include
				case Filters(includes) => if(!orderedFilters.isEmpty) sys.error("Cannot define multiple ordered test filters.") else orderedFilters = includes
				case Exclude(exclude) => excludeTestsSet ++= exclude
				case Listeners(listeners) => testListeners ++= listeners
				case Setup(setupFunction) => setup += setupFunction
				case Cleanup(cleanupFunction) => cleanup += cleanupFunction
				case a: Argument => // now handled by whatever constructs `runners`
			}
		}

		if(excludeTestsSet.size > 0)
			log.debug(excludeTestsSet.mkString("Excluding tests: \n\t", "\n\t", ""))
		if(undefinedFrameworks.size > 0)
			log.warn("Arguments defined for test frameworks that are not present:\n\t" + undefinedFrameworks.mkString("\n\t"))

		def includeTest(test: TestDefinition) = !excludeTestsSet.contains(test.name) && testFilters.forall(filter => filter(test.name))
		val filtered0 = discovered.filter(includeTest).toList.distinct
		val tests = if(orderedFilters.isEmpty) filtered0 else orderedFilters.flatMap(f => filtered0.filter(d => f(d.name))).toList.distinct
		new ProcessedOptions(tests, setup.toList, cleanup.toList, testListeners.toList)
	}

	def apply(frameworks: Map[TestFramework, Framework], testLoader: ClassLoader, runners: Map[TestFramework, Runner], discovered: Seq[TestDefinition], config: Execution, log: Logger): Task[Output] =
	{
		val o = processOptions(config, discovered, log)
		testTask(testLoader, frameworks, runners, o.tests, o.setup, o.cleanup, log, o.testListeners, config)
	}

	def testTask(loader: ClassLoader, frameworks: Map[TestFramework, Framework], runners: Map[TestFramework, Runner], tests: Seq[TestDefinition],
		userSetup: Iterable[ClassLoader => Unit], userCleanup: Iterable[ClassLoader => Unit],
		log: Logger, testListeners: Seq[TestReportListener], config: Execution): Task[Output] =
	{
		def fj(actions: Iterable[() => Unit]): Task[Unit] = nop.dependsOn( actions.toSeq.fork( _() ) : _*)
		def partApp(actions: Iterable[ClassLoader => Unit]) = actions.toSeq map {a => () => a(loader) }

		val (frameworkSetup, runnables, frameworkCleanup) =
			TestFramework.testTasks(frameworks, runners, loader, tests, log, testListeners)

		val setupTasks = fj(partApp(userSetup) :+ frameworkSetup)
		val mainTasks =
			if(config.parallel)
				makeParallel(loader, runnables, setupTasks, config.tags)//.toSeq.join
			else
				makeSerial(loader, runnables, setupTasks, config.tags)
		val taggedMainTasks = mainTasks.tagw(config.tags : _*)
		taggedMainTasks map processResults flatMap { results =>
			val cleanupTasks = fj(partApp(userCleanup) :+ frameworkCleanup(results.overall))
			cleanupTasks map { _ => results }
		}
	}
	type TestRunnable = (String, TestFunction)
	
	private def createNestedRunnables(loader: ClassLoader, testFun: TestFunction, nestedTasks: Seq[TestTask]): Seq[(String, TestFunction)] = 
		nestedTasks.view.zipWithIndex map { case (nt, idx) =>
			val testFunDef = testFun.taskDef
			(testFunDef.fullyQualifiedName, TestFramework.createTestFunction(loader, new TaskDef(testFunDef.fullyQualifiedName + "-" + idx, testFunDef.fingerprint, testFunDef.explicitlySpecified, testFunDef.selectors), testFun.runner, nt))
		}

	def makeParallel(loader: ClassLoader, runnables: Iterable[TestRunnable], setupTasks: Task[Unit], tags: Seq[(Tag,Int)]): Task[Map[String,SuiteResult]] =
		toTasks(loader, runnables.toSeq, tags).dependsOn(setupTasks)

	def toTasks(loader: ClassLoader, runnables: Seq[TestRunnable], tags: Seq[(Tag,Int)]): Task[Map[String, SuiteResult]] = {
		val tasks = runnables.map { case (name, test) => toTask(loader, name, test, tags) }
		tasks.join.map( _.foldLeft(Map.empty[String, SuiteResult]) { case (sum, e) =>  
			sum ++ e		  
		} )
	}

	def toTask(loader: ClassLoader, name: String, fun: TestFunction, tags: Seq[(Tag,Int)]): Task[Map[String, SuiteResult]] = {
		val base = task { (name, fun.apply()) }
		val taggedBase = base.tagw(tags : _*).tag(fun.tags.map(ConcurrentRestrictions.Tag(_)) : _*)
		taggedBase flatMap { case (name, (result, nested)) =>
			val nestedRunnables = createNestedRunnables(loader, fun, nested)
			toTasks(loader, nestedRunnables, tags).map( _.updated(name, result) )
		}
	}

	def makeSerial(loader: ClassLoader, runnables: Seq[TestRunnable], setupTasks: Task[Unit], tags: Seq[(Tag,Int)]): Task[List[(String, SuiteResult)]] = 
	{
		@tailrec
		def processRunnable(runnableList: List[TestRunnable], acc: List[(String, SuiteResult)]): List[(String, SuiteResult)] = 
			runnableList match {
				case hd :: rst => 
					val testFun = hd._2
					val (result, nestedTasks) = testFun.apply()
					val nestedRunnables = createNestedRunnables(loader, testFun, nestedTasks)
					processRunnable(nestedRunnables.toList ::: rst, (hd._1, result) :: acc)
				case Nil => acc
			}

		task { processRunnable(runnables.toList, List.empty) } dependsOn(setupTasks)
	}

	def processResults(results: Iterable[(String, SuiteResult)]): Output =
		Output(TestResult.overall(results.map(_._2.result)), results.toMap, Iterable.empty)
	def foldTasks(results: Seq[Task[Output]], parallel: Boolean): Task[Output] = 
		if (parallel)
			reduced(results.toIndexedSeq, {
				case (Output(v1, m1, _), Output(v2, m2, _)) => Output(if (v1.id < v2.id) v2 else v1, m1 ++ m2, Iterable.empty)
			})
		else {
			def sequence(tasks: List[Task[Output]], acc: List[Output]): Task[List[Output]] = tasks match {
				case Nil => task(acc.reverse)
				case hd::tl => hd flatMap { out => sequence(tl, out::acc) }
			}
			sequence(results.toList, List()) map { ress =>
				val (rs, ms) = ress.unzip { e => (e.overall, e.events) }
				Output(TestResult.overall(rs), ms reduce (_ ++ _), Iterable.empty)
			}
		}

	def discover(frameworks: Seq[Framework], analysis: Analysis, log: Logger): (Seq[TestDefinition], Set[String]) =
		discover(frameworks flatMap TestFramework.getFingerprints, allDefs(analysis), log)

	def allDefs(analysis: Analysis) = analysis.apis.internal.values.flatMap(_.api.definitions).toSeq
	def discover(fingerprints: Seq[Fingerprint], definitions: Seq[Definition], log: Logger): (Seq[TestDefinition], Set[String]) =
	{
		val subclasses = fingerprints collect { case sub: SubclassFingerprint => (sub.superclassName, sub.isModule, sub) };
		val annotations = fingerprints collect { case ann: AnnotatedFingerprint => (ann.annotationName, ann.isModule, ann) };
		log.debug("Subclass fingerprints: " + subclasses)
		log.debug("Annotation fingerprints: " + annotations)

		def firsts[A,B,C](s: Seq[(A,B,C)]): Set[A] = s.map(_._1).toSet
		def defined(in: Seq[(String,Boolean,Fingerprint)], names: Set[String], IsModule: Boolean): Seq[Fingerprint] =
			in collect { case (name, IsModule, print) if names(name) => print }

		def toFingerprints(d: Discovered): Seq[Fingerprint] =
			defined(subclasses, d.baseClasses, d.isModule) ++
			defined(annotations, d.annotations, d.isModule)

		val discovered = Discovery(firsts(subclasses), firsts(annotations))(definitions)
		// TODO: To pass in correct explicitlySpecified and selectors
		val tests = for( (df, di) <- discovered; fingerprint <- toFingerprints(di) ) yield new TestDefinition(df.name, fingerprint, false, Array(new SuiteSelector))
		val mains = discovered collect { case (df, di) if di.hasMain => df.name }
		(tests, mains.toSet)
	}

	def showResults(log: Logger, results: Output, noTestsMessage: =>String): Unit =
	{
		val multipleFrameworks = results.summaries.size > 1
		def printSummary(name: String, message: String) 
		{
			if(message.isEmpty)
				log.debug("Summary for " + name + " not available.")
			else
			{
				if(multipleFrameworks) log.info(name)
				log.info(message)
			}
		}

		for (Summary(name, messages) <- results.summaries)
			printSummary(name, messages)
		val noSummary = results.summaries.headOption.forall(_.summaryText.size == 0)
		val printStandard = multipleFrameworks || noSummary
		// Print the standard one-liner statistic if no framework summary is defined, or when > 1 framework is in used.
		if (printStandard)
		{
			val (skippedCount, errorsCount, passedCount, failuresCount, ignoredCount, canceledCount, pendingCount) = 
				results.events.foldLeft((0, 0, 0, 0, 0, 0, 0)) { case ((skippedAcc, errorAcc, passedAcc, failureAcc, ignoredAcc, canceledAcc, pendingAcc), (name, testEvent)) =>
					(skippedAcc + testEvent.skippedCount, errorAcc + testEvent.errorCount, passedAcc + testEvent.passedCount, failureAcc + testEvent.failureCount, 
					 ignoredAcc + testEvent.ignoredCount, canceledAcc + testEvent.canceledCount, pendingAcc + testEvent.pendingCount)
				}
			val totalCount = failuresCount + errorsCount + skippedCount + passedCount
			val base = s"Total $totalCount, Failed $failuresCount, Errors $errorsCount, Passed $passedCount"

			val otherCounts = Seq("Skipped" -> skippedCount, "Ignored" -> ignoredCount, "Canceled" -> canceledCount, "Pending" -> pendingCount)
			val extra = otherCounts.filter(_._2 > 0).map{case(label,count) => s", $label $count" }

			val postfix = base + extra.mkString
			results.overall match {
				case TestResult.Error => log.error("Error: " + postfix)
				case TestResult.Passed => log.info("Passed: " + postfix)
				case TestResult.Failed => log.error("Failed: " + postfix)
			}
		}
		// Let's always print out Failed tests for now
		if (results.events.isEmpty)
			log.info(noTestsMessage)
		else {
			import TestResult.{Error, Failed, Passed}
			import scala.reflect.NameTransformer.decode

			def select(resultTpe: TestResult.Value) = results.events collect {
				case (name, tpe) if tpe.result == resultTpe =>
					decode(name)
			}

			val failures = select(Failed)
			val errors = select(Error)
			val passed = select(Passed)

			def show(label: String, level: Level.Value, tests: Iterable[String]): Unit =
				if(!tests.isEmpty)
				{
					log.log(level, label)
					log.log(level, tests.mkString("\t", "\n\t", ""))
				}

			show("Passed tests:", Level.Debug, passed )
			show("Failed tests:", Level.Error, failures)
			show("Error during tests:", Level.Error, errors)
		}

		results.overall match {
			case TestResult.Error | TestResult.Failed => throw new TestsFailedException
			case TestResult.Passed => 
		}
	}
}

final class TestsFailedException extends RuntimeException("Tests unsuccessful") with FeedbackProvidedException