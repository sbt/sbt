/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Steven Blundy, Mark Harrah
 */
package sbt

	import java.net.URLClassLoader
	import org.scalatools.testing.{TestFingerprint => Fingerprint, Framework, Runner, Logger=>TLogger}

object Result extends Enumeration
{
	val Error, Passed, Failed = Value
}

object TestFrameworks
{
	val ScalaCheck = new TestFramework("org.scalacheck.FrameworkImpl")
	val ScalaTest = new TestFramework("org.scalatest.FrameworkImpl")
	val Specs = new TestFramework("org.specs.FrameworkImpl")
}
class TestFramework(val implClassName: String) extends NotNull
{
	def create(loader: ClassLoader): Framework =
		Class.forName(implClassName, true, loader).newInstance.asInstanceOf[Framework]
}
final class TestRunner(framework: Framework, loader: ClassLoader, listeners: Seq[TestReportListener], log: Logger) extends NotNull
{
	private[this] val delegate = framework.testRunner(loader, listeners.flatMap(_.contentLogger).toArray)
	final def run(testDefinition: TestDefinition, args: Seq[String]): Result.Value =
	{
		val testClass = testDefinition.testClassName
		def runTest() =
		{
			val results = delegate.run(testClass, testDefinition, args.toArray)
			val event = TestEvent(results)
			safeListenersCall(_.testEvent( event ))
			event.result
		}

		safeListenersCall(_.startGroup(testClass))
		try
		{
			val result = runTest().getOrElse(Result.Passed)
			safeListenersCall(_.endGroup(testClass, result))
			result
		}
		catch
		{
			case e =>
				safeListenersCall(_.endGroup(testClass, e))
				Result.Error
		}
	}

	protected def safeListenersCall(call: (TestReportListener) => Unit): Unit =
		TestFramework.safeForeach(listeners, log)(call)
}

final class NamedTestTask(val name: String, action: => Option[String]) extends NotNull { def run() = action }
object TestFramework
{
	def runTests(frameworks: Seq[TestFramework], classpath: Iterable[Path], tests: Seq[TestDefinition], log: Logger,
		listeners: Seq[TestReportListener]) =
	{
		val (start, runTests, end) = testTasks(frameworks, classpath, tests, log, listeners, true, Nil, Nil)
		def run(tasks: Iterable[NamedTestTask]) = tasks.foreach(_.run())
		run(start)
		run(runTests)
		run(end)
	}
	
	private val ScalaCompilerJarPackages = "scala.tools.nsc." :: "jline." :: "ch.epfl.lamp." :: Nil

	private val TestStartName = "test-start"
	private val TestFinishName = "test-finish"
	
	private[sbt] def safeForeach[T](it: Iterable[T], log: Logger)(f: T => Unit): Unit =
		it.foreach(i => Control.trapAndLog(log){ f(i) } )

		import scala.collection.{Map, Set}
	
	def testTasks(frameworks: Seq[TestFramework], classpath: Iterable[Path], tests: Seq[TestDefinition], log: Logger,
		listeners: Seq[TestReportListener], endErrorsEnabled: Boolean, setup: Iterable[() => Option[String]],
		cleanup: Iterable[() => Option[String]]): (Iterable[NamedTestTask], Iterable[NamedTestTask], Iterable[NamedTestTask]) =
	{
		val loader = createTestLoader(classpath)
		val rawFrameworks = frameworks.map(_.create(loader))
		val mappedTests = testMap(rawFrameworks, tests)
		if(mappedTests.isEmpty)
			(new NamedTestTask(TestStartName, None) :: Nil, Nil, new NamedTestTask(TestFinishName, { log.info("No tests to run."); None }) :: Nil )
		else
			createTestTasks(loader, mappedTests, log, listeners, endErrorsEnabled, setup, cleanup)
	}
	private def testMap(frameworks: Seq[Framework], tests: Seq[TestDefinition]): Map[Framework, Set[TestDefinition]] =
	{
		import scala.collection.mutable.{HashMap, HashSet, Set}
		val map = new HashMap[Framework, Set[TestDefinition]]
		def assignTests(): Unit =
		{
			for(test <- tests if !map.values.exists(_.contains(test)))
			{
				def isTestForFramework(framework: Framework) = framework.tests.exists(matches)
				def matches(fingerprint: Fingerprint) =
					(fingerprint.isModule == test.isModule) &&
					fingerprint.superClassName == test.superClassName
				
				for(framework <- frameworks.find(isTestForFramework))
					map.getOrElseUpdate(framework, new HashSet[TestDefinition]) += test
			}
		}
		if(!frameworks.isEmpty)
			assignTests()
		wrap.Wrappers.readOnly(map)
	}
	private def createTasks(work: Iterable[() => Option[String]], baseName: String) =
		work.toList.zipWithIndex.map{ case (work, index) => new NamedTestTask(baseName + " " + (index+1), work()) }
		
	private def createTestTasks(loader: ClassLoader, tests: Map[Framework, Set[TestDefinition]], log: Logger,
		listeners: Seq[TestReportListener], endErrorsEnabled: Boolean, setup: Iterable[() => Option[String]],
		cleanup: Iterable[() => Option[String]]) =
	{
		val testsListeners = listeners.filter(_.isInstanceOf[TestsListener]).map(_.asInstanceOf[TestsListener])
		def foreachListenerSafe(f: TestsListener => Unit): Unit = safeForeach(testsListeners, log)(f)
		
			import Result.{Error,Passed,Failed}
		object result
		{
			private[this] var value: Result.Value = Passed
			def apply() = synchronized { value }
			def update(v: Result.Value): Unit = synchronized { if(value != Error) value = v }
		}
		val startTask = new NamedTestTask(TestStartName, {foreachListenerSafe(_.doInit); None}) :: createTasks(setup, "Test setup")
		val testTasks =
			tests flatMap { case (framework, testDefinitions) =>
			
					val runner = new TestRunner(framework, loader, listeners, log)
					for(testDefinition <- testDefinitions) yield
					{
						def runTest() =
						{
							val oldLoader = Thread.currentThread.getContextClassLoader
							Thread.currentThread.setContextClassLoader(loader)
							try {
								runner.run(testDefinition, Nil) match
								{
									case Error => result() = Error; Some("ERROR occurred during testing.")
									case Failed => result() = Failed; Some("Test FAILED")
									case _ => None
								}
							}
							finally {
								Thread.currentThread.setContextClassLoader(oldLoader)
							}
						}
						new NamedTestTask(testDefinition.testClassName, runTest())
					}
			}
		def end() =
		{
			foreachListenerSafe(_.doComplete(result()))
			result() match
			{
				case Error => if(endErrorsEnabled) Some("ERROR occurred during testing.") else None
				case Failed => if(endErrorsEnabled) Some("One or more tests FAILED.") else None
				case Passed =>
				{
					log.info(" ")
					log.info("All tests PASSED.")
					None
				}
			}
		}
		val endTask = new NamedTestTask(TestFinishName, end() ) :: createTasks(cleanup, "Test cleanup")
		(startTask, testTasks, endTask)
	}
	private def createTestLoader(classpath: Iterable[Path]): ClassLoader = 
	{
		val filterCompilerLoader = new FilteredLoader(getClass.getClassLoader, ScalaCompilerJarPackages)
		new URLClassLoader(classpath.map(_.asURL).toSeq.toArray, filterCompilerLoader)
	}
}
