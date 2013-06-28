/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Steven Blundy, Mark Harrah, Josh Cough
 */
package sbt

	import java.io.File
	import java.net.URLClassLoader
	import testing.{Logger=>TLogger, Task => TestTask, _}
	import org.scalatools.testing.{Framework => OldFramework}
	import classpath.{ClasspathUtilities, DualLoader, FilteredLoader}
	import scala.annotation.tailrec

object TestResult extends Enumeration
{
	val Passed, Failed, Error = Value
}

object TestFrameworks
{
	val ScalaCheck = new TestFramework("org.scalacheck.ScalaCheckFramework")
	val ScalaTest = new TestFramework("org.scalatest.tools.Framework", "org.scalatest.tools.ScalaTestFramework")
	val Specs = new TestFramework("org.specs.runner.SpecsFramework")
	val Specs2 = new TestFramework("org.specs2.runner.Specs2Framework", "org.specs2.runner.SpecsFramework")
	val JUnit = new TestFramework("com.novocode.junit.JUnitFramework")
}

case class TestFramework(val implClassNames: String*)
{
	@tailrec
	private def createFramework(loader: ClassLoader, log: Logger, frameworkClassNames: List[String]): Option[Framework] = {
		frameworkClassNames match {
			case head :: tail => 
				try 
				{
					Some(Class.forName(head, true, loader).newInstance match {
						case newFramework: Framework => newFramework
						case oldFramework: OldFramework => new FrameworkWrapper(oldFramework)
					})
				}
				catch 
				{ 
					case e: ClassNotFoundException => 
						log.debug("Framework implementation '" + head + "' not present."); 
						createFramework(loader, log, tail)
				}
			case Nil => 
				None
		}
	}

	def create(loader: ClassLoader, log: Logger): Option[Framework] =
		createFramework(loader, log, implClassNames.toList)
}
final class TestDefinition(val name: String, val fingerprint: Fingerprint, val explicitlySpecified: Boolean, val selectors: Array[Selector])
{
	override def toString = "Test " + name + " : " + TestFramework.toString(fingerprint)
	override def equals(t: Any) =
		t match
		{
			case r: TestDefinition => name == r.name && TestFramework.matches(fingerprint, r.fingerprint)
			case _ => false
		}
	override def hashCode: Int = (name.hashCode, TestFramework.hashCode(fingerprint)).hashCode
}

final class TestRunner(delegate: Runner, listeners: Seq[TestReportListener], log: Logger) {

    final def tasks(testDefs: Set[TestDefinition]): Array[TestTask] = 
      delegate.tasks(testDefs.map(df => new TaskDef(df.name, df.fingerprint, df.explicitlySpecified, df.selectors)).toArray)

	final def run(taskDef: TaskDef, testTask: TestTask): (SuiteResult, Seq[TestTask]) =
	{
        val testDefinition = new TestDefinition(taskDef.fullyQualifiedName, taskDef.fingerprint, taskDef.explicitlySpecified, taskDef.selectors)
		log.debug("Running " + taskDef)
		val name = testDefinition.name
		
		def runTest() =
		{
			// here we get the results! here is where we'd pass in the event listener
			val results = new scala.collection.mutable.ListBuffer[Event]
			val handler = new EventHandler { def handle(e:Event){ results += e } }
			val loggers = listeners.flatMap(_.contentLogger(testDefinition))
			val nestedTasks =
				try testTask.execute(handler, loggers.map(_.log).toArray)
				finally loggers.foreach( _.flush() ) 
			val event = TestEvent(results)
			safeListenersCall(_.testEvent( event ))
			(SuiteResult(results), nestedTasks.toSeq)
		}

		safeListenersCall(_.startGroup(name))
		try
		{
			val (suiteResult, nestedTasks) = runTest()
			safeListenersCall(_.endGroup(name, suiteResult.result))
			(suiteResult, nestedTasks)
		}
		catch
		{
			case e: Throwable =>
				safeListenersCall(_.endGroup(name, e))
				(SuiteResult.Error, Seq.empty[TestTask])
		}
	}

	protected def safeListenersCall(call: (TestReportListener) => Unit): Unit =
		TestFramework.safeForeach(listeners, log)(call)
}

object TestFramework
{ 
	def getFingerprints(framework: Framework): Seq[Fingerprint] =
		framework.getClass.getMethod("fingerprints").invoke(framework) match
		{
			case fingerprints: Array[Fingerprint] => fingerprints.toList
			case _ => sys.error("Could not call 'fingerprints' on framework " + framework)
		}

	private val ScalaCompilerJarPackages = "scala.tools." :: "jline." :: "ch.epfl.lamp." :: Nil

	private val TestStartName = "test-start"
	private val TestFinishName = "test-finish"
	
	private[sbt] def safeForeach[T](it: Iterable[T], log: Logger)(f: T => Unit): Unit =
		it.foreach(i => try f(i) catch { case e: Exception => log.trace(e); log.error(e.toString) })

	private[sbt] def hashCode(f: Fingerprint): Int = f match {
		case s: SubclassFingerprint => (s.isModule, s.superclassName).hashCode
		case a: AnnotatedFingerprint => (a.isModule, a.annotationName).hashCode
		case _ => 0
	}
	def matches(a: Fingerprint, b: Fingerprint) =
		(a, b) match
		{
			case (a: SubclassFingerprint, b: SubclassFingerprint) => a.isModule == b.isModule && a.superclassName == b.superclassName
			case (a: AnnotatedFingerprint, b: AnnotatedFingerprint) => a.isModule == b.isModule && a.annotationName == b.annotationName
			case _ => false
		}
	def toString(f: Fingerprint): String =
		f match
		{
			case sf: SubclassFingerprint => "subclass(" + sf.isModule + ", " + sf.superclassName + ")"
			case af: AnnotatedFingerprint => "annotation(" + af.isModule + ", " + af.annotationName + ")"
			case _ => f.toString
		}

	def testTasks(frameworks: Map[TestFramework, Framework],
		runners: Map[TestFramework, Runner], 
		testLoader: ClassLoader,
		tests: Seq[TestDefinition],
		log: Logger,
		listeners: Seq[TestReportListener],
		testArgsByFramework: Map[Framework, Seq[String]]):
			(() => Unit, Seq[(String, TestFunction)], TestResult.Value => () => Unit) =
	{
		val arguments = testArgsByFramework withDefaultValue Nil
		val mappedTests = testMap(frameworks.values.toSeq, tests, arguments)
		if(mappedTests.isEmpty)
			(() => (), Nil, _ => () => () )
		else
			createTestTasks(testLoader, runners.map { case (tf, r) => (frameworks(tf), new TestRunner(r, listeners, log))}, mappedTests, tests, log, listeners)
	}

	private[this] def order(mapped: Map[String, TestFunction], inputs: Seq[TestDefinition]): Seq[(String, TestFunction)] =
		for( d <- inputs; act <- mapped.get(d.name) ) yield (d.name, act)

	private[this] def testMap(frameworks: Seq[Framework], tests: Seq[TestDefinition], args: Map[Framework, Seq[String]]):
		Map[Framework, (Set[TestDefinition], Seq[String])] =
	{
		import scala.collection.mutable.{HashMap, HashSet, Set}
		val map = new HashMap[Framework, Set[TestDefinition]]
		def assignTests()
		{
			for(test <- tests if !map.values.exists(_.contains(test)))
			{
				def isTestForFramework(framework: Framework) = getFingerprints(framework).exists {t => matches(t, test.fingerprint) }
				for(framework <- frameworks.find(isTestForFramework))
					map.getOrElseUpdate(framework, new HashSet[TestDefinition]) += test
			}
		}
		if(!frameworks.isEmpty)
			assignTests()
		map.toMap transform { (framework, tests) => ( mergeDuplicates(framework, tests.toSeq), args(framework)) };
	}
	private[this] def mergeDuplicates(framework: Framework, tests: Seq[TestDefinition]): Set[TestDefinition] =
	{
		val frameworkPrints = framework.fingerprints.reverse
		def pickOne(prints: Seq[Fingerprint]): Fingerprint =
			frameworkPrints.find(prints.toSet) getOrElse prints.head
		val uniqueDefs =
			for( ((name, explicitlySpecified, selectors), defs) <- tests.groupBy(t => (t.name, t.explicitlySpecified, t.selectors)) ) yield
				new TestDefinition(name, pickOne(defs.map(_.fingerprint)), explicitlySpecified, selectors)
		uniqueDefs.toSet
	}

	private def createTestTasks(loader: ClassLoader, runners: Map[Framework, TestRunner], tests: Map[Framework, (Set[TestDefinition], Seq[String])], ordered: Seq[TestDefinition], log: Logger, listeners: Seq[TestReportListener]) =
	{
		val testsListeners = listeners collect { case tl: TestsListener => tl }

		def foreachListenerSafe(f: TestsListener => Unit): () => Unit = () => safeForeach(testsListeners, log)(f)

		import TestResult.{Error,Passed,Failed}

		val startTask = foreachListenerSafe(_.doInit)
		val testTasks =
			tests flatMap { case (framework, (testDefinitions, testArgs)) =>
				val runner = runners(framework)
				val testTasks = withContextLoader(loader) { runner.tasks(testDefinitions) }
				for (testTask <- testTasks) yield {
				  val taskDef = testTask.taskDef
				  (taskDef.fullyQualifiedName, createTestFunction(loader, taskDef, runner, testTask))
				}
			}

		val endTask = (result: TestResult.Value) => foreachListenerSafe(_.doComplete(result))
		(startTask, order(testTasks, ordered), endTask)
	}
	private[this] def withContextLoader[T](loader: ClassLoader)(eval: => T): T =
	{
		val oldLoader = Thread.currentThread.getContextClassLoader
		Thread.currentThread.setContextClassLoader(loader)
		try { eval } finally { Thread.currentThread.setContextClassLoader(oldLoader) }
	}
	def createTestLoader(classpath: Seq[File], scalaInstance: ScalaInstance, tempDir: File): ClassLoader =
	{
		val interfaceJar = IO.classLocationFile(classOf[testing.Framework])
		val interfaceFilter = (name: String) => name.startsWith("org.scalatools.testing.") || name.startsWith("sbt.testing.")
		val notInterfaceFilter = (name: String) => !interfaceFilter(name)
		val dual = new DualLoader(scalaInstance.loader, notInterfaceFilter, x => true, getClass.getClassLoader, interfaceFilter, x => false)
		val main = ClasspathUtilities.makeLoader(classpath, dual, scalaInstance, tempDir)
		ClasspathUtilities.filterByClasspath(interfaceJar +: classpath, main)
	}
	def createTestFunction(loader: ClassLoader, taskDef: TaskDef, runner:TestRunner, testTask: TestTask): TestFunction = 
		new TestFunction(taskDef, runner, (r: TestRunner) => withContextLoader(loader) { r.run(taskDef, testTask) }) { def tags = testTask.tags }
}

abstract class TestFunction(val taskDef: TaskDef, val runner: TestRunner, fun: (TestRunner) => (SuiteResult, Seq[TestTask])) {

	def apply(): (SuiteResult, Seq[TestTask]) = fun(runner)

	def tags: Seq[String]
}
