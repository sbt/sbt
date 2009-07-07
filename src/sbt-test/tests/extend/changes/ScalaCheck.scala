/** This tests implementing a test framework in a project definition.  To ensure sbt's builtin ScalaCheck
* test framework is not used, it flips success and failure so that a failing test is marked as succeeding and
* a suceeding test is marked as failing. */

package framework

import sbt._

object FrameworkScalaCheck extends LazyTestFramework
{
	val name = "ScalaCheck"

	def testSuperClassName = "org.scalacheck.Properties"
	def testSubClassType = ClassType.Module

	def testRunnerClassName = "framework.RunnerScalaCheck"
}

class RunnerScalaCheck(val log: Logger, val listeners: Seq[TestReportListener], val testLoader: ClassLoader) extends BasicTestRunner
{
	import org.scalacheck.{Pretty, Properties, Test}
	def runTest(testClassName: String): Result.Value =
	{
		val test = ModuleUtilities.getObject(testClassName, testLoader).asInstanceOf[Properties]
		val result = Test.checkProperties(test, Test.defaultParams, propReport, testReport).find(!_._2.passed)
		if(result.isEmpty)
			Result.Failed // intentionally flipped (see top comment)
		else
			Result.Passed // intentionally flipped (see top comment)
	}
	private def propReport(pName: String, s: Int, d: Int) {}
	private def testReport(name: String, res: Test.Result)
	{
		val msg = Pretty.pretty(res)
		if(res.passed)
			log.info("+ " + name + ": " + msg)
		else
			log.error("! " + name + ": " + msg)
		
	}
}