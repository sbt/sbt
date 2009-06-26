/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */

package scripted

import sbt._
import java.io.File

trait ScriptedTestFilter extends NotNull
{
	def accept(group: String, name: String): Boolean
}
class BasicFilter(f: (String, String) => Boolean) extends ScriptedTestFilter
{
	def accept(group: String, name: String) = f(group, name)
}

object AcceptAllFilter extends ScriptedTestFilter
{
	def accept(group: String, name: String): Boolean = true
}
class ScriptedTests(testResources: Resources, filter: ScriptedTestFilter) extends NotNull
{
	def this(resourceBaseDirectory: File, filter: (String, String) => Boolean) = this(new Resources(resourceBaseDirectory), new BasicFilter(filter))
	def this(resourceBaseDirectory: File, filter: ScriptedTestFilter) = this(new Resources(resourceBaseDirectory), filter)
	def this(testResources: Resources) = this(testResources, AcceptAllFilter)
	def this(resourceBaseDirectory: File) = this(new Resources(resourceBaseDirectory))
	
	val ScriptFilename = "test"
	import testResources._
	
	private def includeDirectory(file: File) = file.getName != ".svn"
	def scriptedTests(log: Logger): Option[String] =
	{
		System.setProperty("sbt.scala.version", "")
		var success = true
		for(group <- baseDirectory.listFiles(DirectoryFilter) if includeDirectory(group))
		{
			log.info("Test group " + group.getName)
			for(test <- group.listFiles(DirectoryFilter) if includeDirectory(test))
			{
				val testName = test.getName
				if(!filter.accept(group.getName, testName))
					log.warn(" Test " + testName + " skipped.")
				else
					scriptedTest(test, log) match
					{
						case Some(err) =>
							log.error(" Test " + testName + " failed: " + err)
							success = false
						case None => log.info(" Test " + testName + " succeeded.")
					}
			}
		}
		if(success)
			None
		else
			Some("One or more tests failed.")
	}
	
	def scriptedTest(group: String, name: String, log: Logger): Option[String] =
		readOnlyResourceDirectory(group, name).fold(err => Some(err), testDirectory => scriptedTest(testDirectory, log))
	def scriptedTest(testDirectory: File, log: Logger): Option[String] =
	{
		(for(script <- (new TestScriptParser(testDirectory, log)).parse(new File(testDirectory, ScriptFilename)).right;
			u <- withProject(testDirectory, log)(script).right )
		yield u).left.toOption
	}
}
