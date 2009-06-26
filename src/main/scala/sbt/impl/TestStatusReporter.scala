/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt.impl

import java.io.File
import scala.collection.mutable.{HashMap, Map}

/** Only intended to be used once per instance. */
private[sbt] class TestStatusReporter(path: Path, log: Logger) extends TestsListener
{
	private lazy val succeeded: Map[String, Long] = TestStatus.read(path, log)

	def doInit {}
	def startGroup(name: String) { succeeded removeKey name }
	def testEvent(event: TestEvent) {}
	def endGroup(name: String, t: Throwable) {}
	def endGroup(name: String, result: Result.Value)
	{
		if(result == Result.Passed)
			succeeded(name) = System.currentTimeMillis
	}
	def doComplete(finalResult: Result.Value) { complete() }
	def doComplete(t: Throwable) { complete() }
	
	private def complete()
	{
		TestStatus.write(succeeded, "Successful Tests", path, log)
	}
}

private[sbt] class TestQuickFilter(testAnalysis: CompileAnalysis, failedOnly: Boolean, path: Path, log: Logger) extends (String => Boolean) with NotNull
{
	private lazy val exclude = TestStatus.read(path, log)
	private lazy val map = testAnalysis.testSourceMap
	def apply(test: String) =
		exclude.get(test) match
		{
			case None => true // include because this test has not been run or did not succeed
			case Some(lastSuccessTime) => // succeeded the last time it was run
				if(failedOnly)
					false // don't include because the last time succeeded
				else
					testAnalysis.products(map(test)) match
					{
						case None => true
						case Some(products) => products.exists(lastSuccessTime <= _.lastModified) // include if the test is newer than the last run
					}
		}
}
private object TestStatus
{
	import java.util.Properties
	def read(path: Path, log: Logger): Map[String, Long] =
	{
		val map = new HashMap[String, Long]
		val properties = new Properties
		logError(PropertiesUtilities.load(properties, path, log), "loading", log)
		for(test <- PropertiesUtilities.propertyNames(properties))
			map.put(test, properties.getProperty(test).toLong)
		map
	}
	def write(map: Map[String, Long], label: String, path: Path, log: Logger)
	{
		val properties = new Properties
		for( (test, lastSuccessTime) <- map)
			properties.setProperty(test, lastSuccessTime.toString)
		logError(PropertiesUtilities.write(properties, label, path, log), "writing", log)
	}
	private def logError(result: Option[String], action: String, log: Logger)
	{
		result.foreach(msg => log.error("Error " + action + " test status: " + msg))
	}
}