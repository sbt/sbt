/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */

package sbt.test

import java.io.File

final class ScriptedTests(testResources: Resources) extends NotNull
{
	def this(resourceBaseDirectory: File, additional: ClassLoader) = this(new Resources(resourceBaseDirectory, additional))
	def this(resourceBaseDirectory: File) = this(new Resources(resourceBaseDirectory))
	
	val ScriptFilename = "test"
	import testResources._
	
	def scriptedTest(group: String, name: String, log: Logger): Option[String] =
		readOnlyResourceDirectory(group, name).fold(err => Some(err), testDirectory => scriptedTest(testDirectory, log))
	def scriptedTest(testDirectory: File, log: Logger): Option[String] =
	{
		(for(script <- (new TestScriptParser(testDirectory, log)).parse(new File(testDirectory, ScriptFilename)).right;
			u <- withProject(testDirectory, log)(script).right )
		yield u).left.toOption
	}
}
