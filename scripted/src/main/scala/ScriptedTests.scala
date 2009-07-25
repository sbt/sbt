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
	
	private def printClass(c: Class[_]) = println(c.getName + " loader=" +c.getClassLoader + " location=" + FileUtilities.classLocationFile(c))
	
	def scriptedTest(group: String, name: String, logger: Reflected.Logger): String =
	{
		val log = new RemoteLogger(logger)
		val result = readOnlyResourceDirectory(group, name).fold(err => Some(err), testDirectory => scriptedTest(testDirectory, log))
		wrapOption(result)
	}
	private def scriptedTest(testDirectory: File, log: Logger): Option[String] =
	{
		(for(script <- (new TestScriptParser(testDirectory, log)).parse(new File(testDirectory, ScriptFilename)).right;
			u <- withProject(testDirectory, log)(script).right )
		yield u).left.toOption
	}
	private[this] def wrapOption[T >: Null](s: Option[T]): T = s match { case Some(t) => t; case None => null }
}
