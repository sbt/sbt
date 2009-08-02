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
		translateOption(result)
	}
	private def scriptedTest(testDirectory: File, log: Logger): Option[String] =
	{
		val buffered = new BufferedLogger(log)
		//buffered.startRecording()
		val filtered = new FilterLogger(buffered)
		val parsedScript = (new TestScriptParser(testDirectory, filtered)).parse(new File(testDirectory, ScriptFilename))
		val result = parsedScript.right.flatMap(withProject(testDirectory, filtered))
		//result.left.foreach(x => buffered.playAll())
		//buffered.clearAll()
		result.left.toOption
	}
	private[this] def translateOption[T >: Null](s: Option[T]): T = s match { case Some(t) => t; case None => null }
}

// TODO: remove for sbt 0.5.3
final class FilterLogger(delegate: Logger) extends BasicLogger
{
	def trace(t: => Throwable)
	{
		if(traceEnabled)
			delegate.trace(t)
	}
	def log(level: Level.Value, message: => String)
	{
		if(atLevel(level))
			delegate.log(level, message)
	}
	def success(message: => String)
	{
		if(atLevel(Level.Info))
			delegate.success(message)
	}
	def control(event: ControlEvent.Value, message: => String)
	{
		if(atLevel(Level.Info))
			delegate.control(event, message)
	}
	def logAll(events: Seq[LogEvent]): Unit = events.foreach(delegate.log)
}
