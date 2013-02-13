/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Steven Blundy, Mark Harrah, Josh Cough
 */
package sbt

	import java.io.File
	import java.net.URLClassLoader
	import org.scalatools.testing.{AnnotatedFingerprint, Fingerprint, SubclassFingerprint, TestFingerprint}
	import org.scalatools.testing.{Event, EventHandler, Framework, Runner, Runner2, Logger=>TLogger}
	import classpath.{ClasspathUtilities, DualLoader, FilteredLoader}

object TestResult extends Enumeration
{
	val Passed, Failed, Error = Value
}

object TestFrameworks
{
	val ScalaCheck = new TestFramework("org.scalacheck.ScalaCheckFramework")
	val ScalaTest = new TestFramework("org.scalatest.tools.ScalaTestFramework")
	val Specs = new TestFramework("org.specs.runner.SpecsFramework")
	val Specs2 = new TestFramework("org.specs2.runner.SpecsFramework")
	val JUnit = new TestFramework("com.novocode.junit.JUnitFramework")
}

case class TestFramework(val implClassName: String)
{
	def create(loader: ClassLoader, log: Logger): Option[Framework] =
	{
		try { Some(Class.forName(implClassName, true, loader).newInstance.asInstanceOf[Framework]) }
		catch { case e: ClassNotFoundException => log.debug("Framework implementation '" + implClassName + "' not present."); None }
	}
}
final class TestDefinition(val name: String, val fingerprint: Fingerprint)
{
	override def toString = "Test " + name + " : " + TestFramework.toString(fingerprint)
	override def equals(t: Any) =
		t match
		{
			case r: TestDefinition => name == r.name && TestFramework.matches(fingerprint, r.fingerprint)
			case _ => false
		}
}

final class TestRunner(framework: Framework, loader: ClassLoader, listeners: Seq[TestReportListener], log: Logger)
{
	private[this] def run(testDefinition: TestDefinition, handler: EventHandler, args: Array[String]): Unit =
	{
		val loggers = listeners.flatMap(_.contentLogger(testDefinition))
		val delegate = framework.testRunner(loader, loggers.map(_.log).toArray)
		try { delegateRun(delegate, testDefinition, handler, args) }
		finally { loggers.foreach( _.flush() ) }
	}
	private[this] def delegateRun(delegate: Runner, testDefinition: TestDefinition, handler: EventHandler, args: Array[String]): Unit =
		(testDefinition.fingerprint, delegate) match
		{
			case (simple: TestFingerprint, _) => delegate.run(testDefinition.name, simple, handler, args)
			case (basic, runner2: Runner2) => runner2.run(testDefinition.name, basic, handler, args)
			case _ => error("Framework '" + framework + "' does not support test '" + testDefinition + "'")
		}

	final def run(testDefinition: TestDefinition, args: Seq[String]): TestResult.Value =
	{
		log.debug("Running " + testDefinition + " with arguments " + args.mkString(", "))
		val name = testDefinition.name
		def runTest() =
		{
			// here we get the results! here is where we'd pass in the event listener
			val results = new scala.collection.mutable.ListBuffer[Event]
			val handler = new EventHandler { def handle(e:Event){ results += e } }
			run(testDefinition, handler, args.toArray)
			val event = TestEvent(results)
			safeListenersCall(_.testEvent( event ))
			event.result
		}

		safeListenersCall(_.startGroup(name))
		try
		{
			val result = runTest().getOrElse(TestResult.Passed)
			safeListenersCall(_.endGroup(name, result))
			result
		}
		catch
		{
			case e: Throwable =>
				safeListenersCall(_.endGroup(name, e))
				TestResult.Error
		}
	}

	protected def safeListenersCall(call: (TestReportListener) => Unit): Unit =
		TestFramework.safeForeach(listeners, log)(call)
}

object TestFramework
{
	def getTests(framework: Framework): Seq[Fingerprint] =
		framework.getClass.getMethod("tests").invoke(framework) match
		{
			case newStyle: Array[Fingerprint] => newStyle.toList
			case oldStyle: Array[TestFingerprint] => oldStyle.toList
			case _ => error("Could not call 'tests' on framework " + framework)
		}

	private val ScalaCompilerJarPackages = "scala.tools." :: "jline." :: "ch.epfl.lamp." :: Nil

	private val TestStartName = "test-start"
	private val TestFinishName = "test-finish"
	
	private[sbt] def safeForeach[T](it: Iterable[T], log: Logger)(f: T => Unit): Unit =
		it.foreach(i => try f(i) catch { case e: Exception => log.trace(e); log.error(e.toString) })

	def matches(a: Fingerprint, b: Fingerprint) =
		(a, b) match
		{
			case (a: SubclassFingerprint, b: SubclassFingerprint) => a.isModule == b.isModule && a.superClassName == b.superClassName
			case (a: AnnotatedFingerprint, b: AnnotatedFingerprint) => a.isModule == b.isModule && a.annotationName == b.annotationName
			case _ => false
		}
	def toString(f: Fingerprint): String =
		f match
		{
			case sf: SubclassFingerprint => "subclass(" + sf.isModule + ", " + sf.superClassName + ")"
			case af: AnnotatedFingerprint => "annotation(" + af.isModule + ", " + af.annotationName + ")"
			case _ => f.toString
		}

	def testTasks(frameworks: Seq[Framework],
		testLoader: ClassLoader,
		tests: Seq[TestDefinition],
		log: Logger,
		listeners: Seq[TestReportListener],
		testArgsByFramework: Map[Framework, Seq[String]]):
			(() => Unit, Seq[(String, () => TestResult.Value)], TestResult.Value => () => Unit) =
	{
		val arguments = testArgsByFramework withDefaultValue Nil
		val mappedTests = testMap(frameworks, tests, arguments)
		if(mappedTests.isEmpty)
			(() => (), Nil, _ => () => () )
		else
			createTestTasks(testLoader, mappedTests, tests, log, listeners)
	}

	private[this] def order(mapped: Map[String, () => TestResult.Value], inputs: Seq[TestDefinition]): Seq[(String, () => TestResult.Value)] =
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
				def isTestForFramework(framework: Framework) = getTests(framework).exists {t => matches(t, test.fingerprint) }
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
		val frameworkPrints = framework.tests.reverse
		def pickOne(prints: Seq[Fingerprint]): Fingerprint =
			frameworkPrints.find(prints.toSet) getOrElse prints.head
		val uniqueDefs =
			for( (name, defs) <- tests.groupBy(_.name) ) yield
				new TestDefinition(name, pickOne(defs.map(_.fingerprint)))
		uniqueDefs.toSet
	}
		
	private def createTestTasks(loader: ClassLoader, tests: Map[Framework, (Set[TestDefinition], Seq[String])], ordered: Seq[TestDefinition], log: Logger, listeners: Seq[TestReportListener]) =
	{
		val testsListeners = listeners collect { case tl: TestsListener => tl }
		def foreachListenerSafe(f: TestsListener => Unit): () => Unit = () => safeForeach(testsListeners, log)(f)
		
			import TestResult.{Error,Passed,Failed}

		val startTask = foreachListenerSafe(_.doInit)
		val testTasks =
			tests flatMap { case (framework, (testDefinitions, testArgs)) =>
			
					val runner = new TestRunner(framework, loader, listeners, log)
					for(testDefinition <- testDefinitions) yield
					{
						val runTest = () => withContextLoader(loader) { runner.run(testDefinition, testArgs) }
						(testDefinition.name, runTest)
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
		val declaresCompiler = classpath.exists(_.getName contains "scala-compiler")
		val filterCompilerLoader = if(declaresCompiler) scalaInstance.loader else new FilteredLoader(scalaInstance.loader, ScalaCompilerJarPackages)
		val interfaceFilter = (name: String) => name.startsWith("org.scalatools.testing.")
		val notInterfaceFilter = (name: String) => !interfaceFilter(name)
		val dual = new DualLoader(filterCompilerLoader, notInterfaceFilter, x => true, getClass.getClassLoader, interfaceFilter, x => false)
		ClasspathUtilities.makeLoader(classpath, dual, scalaInstance, tempDir)
	}
}
