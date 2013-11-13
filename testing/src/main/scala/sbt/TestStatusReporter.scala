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

	override def startSuite(name: String) { succeeded remove name }
	override def endSuite(name: String, suite: SuiteReport) {
		if(suite.result.result == TestResult.Passed)
			succeeded(name) = System.currentTimeMillis
	}
	override def doComplete(finalResult: TestResult.Value) {
		TestStatus.write(succeeded, "Successful Tests", f)
	}
}

private[sbt] object TestStatus
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
