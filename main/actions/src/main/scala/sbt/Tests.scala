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

	import testing.{AnnotatedFingerprint, Fingerprint, Framework, SubclassFingerprint, Runner}

	import java.io.File

sealed trait TestOption
object Tests
{
	// (overall result, individual results)
	type Output = (TestResult.Value, Map[String,TestResult.Value])
	
	final case class Setup(setup: ClassLoader => Unit) extends TestOption
	def Setup(setup: () => Unit) = new Setup(_ => setup())

	final case class Cleanup(cleanup: ClassLoader => Unit) extends TestOption
	def Cleanup(setup: () => Unit) = new Cleanup(_ => setup())

	final case class Exclude(tests: Iterable[String]) extends TestOption
	final case class Listeners(listeners: Iterable[TestReportListener]) extends TestOption
	final case class Filter(filterTest: String => Boolean) extends TestOption
	/** Test execution will be ordered by the position of the matching filter. */
	final case class Filters(filterTest: Seq[String => Boolean]) extends TestOption

	// args for all frameworks
	def Argument(args: String*): Argument = Argument(None, args.toList)
	// args for a particular test framework
	def Argument(tf: TestFramework, args: String*): Argument = Argument(Some(tf), args.toList)

	// None means apply to all, Some(tf) means apply to a particular framework only.
	final case class Argument(framework: Option[TestFramework], args: List[String]) extends TestOption

	final case class Execution(options: Seq[TestOption], parallel: Boolean, tags: Seq[(Tag, Int)])

	def apply(frameworks: Map[TestFramework, Framework], testLoader: ClassLoader, runners: Map[TestFramework, Runner], discovered: Seq[TestDefinition], config: Execution, log: Logger): Task[Output] =
	{
			import collection.mutable.{HashSet, ListBuffer, Map, Set}
		val testFilters = new ListBuffer[String => Boolean]
		var orderedFilters = Seq[String => Boolean]()
		val excludeTestsSet = new HashSet[String]
		val setup, cleanup = new ListBuffer[ClassLoader => Unit]
		val testListeners = new ListBuffer[TestReportListener]
		val testArgsByFramework = Map[Framework, ListBuffer[String]]()
		val undefinedFrameworks = new ListBuffer[String]
		def frameworkArgs(framework: Framework, args: Seq[String]): Unit =
			testArgsByFramework.getOrElseUpdate(framework, new ListBuffer[String]) ++= args
		def frameworkArguments(framework: TestFramework, args: Seq[String]): Unit =
			(frameworks get framework) match {
				case Some(f) => frameworkArgs(f, args)
				case None => undefinedFrameworks ++= framework.implClassNames
			}

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
				/**
					* There are two cases here.
					* The first handles TestArguments in the project file, which
					* might have a TestFramework specified.
					* The second handles arguments to be applied to all test frameworks.
					*   -- arguments from the project file that didnt have a framework specified
					*   -- command line arguments (ex: test-only someClass -- someArg)
					*      (currently, command line args must be passed to all frameworks)
					*/
				case Argument(Some(framework), args) => frameworkArguments(framework, args)
				case Argument(None, args) => frameworks.values.foreach { f => frameworkArgs(f, args) }
			}
		}

		if(excludeTestsSet.size > 0)
			log.debug(excludeTestsSet.mkString("Excluding tests: \n\t", "\n\t", ""))
		if(undefinedFrameworks.size > 0)
			log.warn("Arguments defined for test frameworks that are not present:\n\t" + undefinedFrameworks.mkString("\n\t"))

		def includeTest(test: TestDefinition) = !excludeTestsSet.contains(test.name) && testFilters.forall(filter => filter(test.name))
		val filtered0 = discovered.filter(includeTest).toList.distinct
		val tests = if(orderedFilters.isEmpty) filtered0 else orderedFilters.flatMap(f => filtered0.filter(d => f(d.name))).toList.distinct
		val arguments = testArgsByFramework.map { case (k,v) => (k, v.toList) } toMap;
		testTask(testLoader, frameworks, runners, tests, setup.readOnly, cleanup.readOnly, log, testListeners.readOnly, arguments, config)
	}

	def testTask(loader: ClassLoader, frameworks: Map[TestFramework, Framework], runners: Map[TestFramework, Runner], tests: Seq[TestDefinition],
		userSetup: Iterable[ClassLoader => Unit], userCleanup: Iterable[ClassLoader => Unit],
		log: Logger, testListeners: Seq[TestReportListener], arguments: Map[Framework, Seq[String]], config: Execution): Task[Output] =
	{
		def fj(actions: Iterable[() => Unit]): Task[Unit] = nop.dependsOn( actions.toSeq.fork( _() ) : _*)
		def partApp(actions: Iterable[ClassLoader => Unit]) = actions.toSeq map {a => () => a(loader) }

		val (frameworkSetup, runnables, frameworkCleanup) =
			TestFramework.testTasks(frameworks, runners, loader, tests, log, testListeners, arguments)

		val setupTasks = fj(partApp(userSetup) :+ frameworkSetup)
		val mainTasks =
			if(config.parallel)
				makeParallel(runnables, setupTasks, config.tags).toSeq.join
			else
				makeSerial(runnables, setupTasks, config.tags)
		val taggedMainTasks = mainTasks.tagw(config.tags : _*)
		taggedMainTasks map processResults flatMap { results =>
			val cleanupTasks = fj(partApp(userCleanup) :+ frameworkCleanup(results._1))
			cleanupTasks map { _ => results }
		}
	}
	type TestRunnable = (String, () => TestResult.Value)
	def makeParallel(runnables: Iterable[TestRunnable], setupTasks: Task[Unit], tags: Seq[(Tag,Int)]) =
		runnables map { case (name, test) => task { (name, test()) } tagw(tags : _*) dependsOn setupTasks named name }
	def makeSerial(runnables: Seq[TestRunnable], setupTasks: Task[Unit], tags: Seq[(Tag,Int)]) =
		task { runnables map { case (name, test) => (name, test()) } } dependsOn(setupTasks)

	def processResults(results: Iterable[(String, TestResult.Value)]): (TestResult.Value, Map[String, TestResult.Value]) =
		(overall(results.map(_._2)), results.toMap)
	def foldTasks(results: Seq[Task[Output]], parallel: Boolean): Task[Output] =
		if (parallel)
			reduced(results.toIndexedSeq, {
				case ((v1, m1), (v2, m2)) => (if (v1.id < v2.id) v2 else v1, m1 ++ m2)
			})
		else {
			def sequence(tasks: List[Task[Output]], acc: List[Output]): Task[List[Output]] = tasks match {
				case Nil => task(acc.reverse)
				case hd::tl => hd flatMap { out => sequence(tl, out::acc) }
			}
			sequence(results.toList, List()) map { ress =>
				val (rs, ms) = ress.unzip
				(overall(rs), ms reduce (_ ++ _))
			}
		}
	def overall(results: Iterable[TestResult.Value]): TestResult.Value =
		(TestResult.Passed /: results) { (acc, result) => if(acc.id < result.id) result else acc }
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
		val tests = for( (df, di) <- discovered; fingerprint <- toFingerprints(di) ) yield new TestDefinition(df.name, fingerprint)
		val mains = discovered collect { case (df, di) if di.hasMain => df.name }
		(tests, mains.toSet)
	}

	def showResults(log: Logger, results: (TestResult.Value, Map[String, TestResult.Value]), noTestsMessage: =>String): Unit =
	{
		if (results._2.isEmpty)
			log.info(noTestsMessage)
		else {
			import TestResult.{Error, Failed, Passed}

			def select(Tpe: TestResult.Value) = results._2 collect { case (name, Tpe) => name }

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

			if(!failures.isEmpty || !errors.isEmpty)
				throw new TestsFailedException
		}
	}

	sealed trait TestRunPolicy
	case object InProcess extends TestRunPolicy
	final case class SubProcess(config: ForkOptions) extends TestRunPolicy
	object SubProcess {
		@deprecated("Construct SubProcess with a ForkOptions argument.", "0.13.0")
		def apply(javaOptions: Seq[String]): SubProcess = SubProcess(ForkOptions(runJVMOptions = javaOptions))
	}

	final case class Group(name: String, tests: Seq[TestDefinition], runPolicy: TestRunPolicy)
}

final class TestsFailedException extends RuntimeException("Tests unsuccessful") with FeedbackProvidedException