/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.util.regex.Pattern
	import BuildStreams.{Streams, TaskStreams}
	import Project.ScopedKey

object Output
{
	def showMatches(pattern: Pattern)(line: String): Option[String] =
	{
		val matcher = pattern.matcher(line)
		if(ConsoleLogger.formatEnabled)
		{
			val highlighted = matcher.replaceAll(scala.Console.RED + "$0" + scala.Console.RESET)
			if(highlighted == line) None else Some(highlighted)
		}
		else if(matcher.find)
			Some(line)
		else
			None
	}

	def last(key: Option[ScopedKey[_]], mgr: Streams): Unit =
		printLines(lastLines(key, mgr))
	def printLines(lines: Seq[String]) = lines foreach println
	def lastGrep(key: Option[ScopedKey[_]], mgr: Streams, patternString: String)
	{
		val pattern = Pattern.compile(patternString)
		printLines(lastLines(key, mgr).flatMap(showMatches(pattern)) )
	}
	def lastLines(key: Option[ScopedKey[_]], mgr: Streams): Seq[String] =
		lastLines(key getOrElse Project.globalLoggerKey, mgr)
	def lastLines(key: ScopedKey[_], mgr: Streams): Seq[String] =
		mgr.use(key) { s => IO.readLines(s.readText( Project.fillTaskAxis(key) )) }
}
