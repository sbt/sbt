/* sbt -- Simple Build Tool
 * Copyright 2009, 2010, 2011, 2012 Mark Harrah
 */
package sbt

import java.io.File

import scala.collection.mutable.Map

// Assumes exclusive ownership of the file.
private[sbt] class TestStatusReporter(f: File) extends TestsListener
{
	private lazy val succeeded = TestStatus.read(f)

	def doInit {}
	def startGroup(name: String) { succeeded remove name }
	def testEvent(event: TestEvent) {}
	def endGroup(name: String, t: Throwable) {}
	def endGroup(name: String, result: TestResult.Value) {
		if(result == TestResult.Passed)
			succeeded(name) = System.currentTimeMillis
	}
	def doComplete(finalResult: TestResult.Value) {
		TestStatus.write(succeeded, "Successful Tests", f)
	}
}

private[sbt] class TestResultFilter(f: File) extends (String => Boolean) with NotNull
{
	private lazy val succeeded = TestStatus.read(f)
	def apply(test: String) = succeeded.contains(test)
}

private object TestStatus
{
	import java.util.Properties
	def read(f: File): Map[String, Long] =
	{
		import scala.collection.JavaConversions.{enumerationAsScalaIterator, propertiesAsScalaMap}
		val properties = new Properties
		IO.load(properties, f)
		properties map {case (k, v) => (k, v.toLong)}
	}
	def write(map: Map[String, Long], label: String, f: File)
	{
		val properties = new Properties
		for( (test, lastSuccessTime) <- map)
			properties.setProperty(test, lastSuccessTime.toString)
		IO.write(properties, label, f)
	}
}
