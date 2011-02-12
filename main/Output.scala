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

	def last(scope: Scope, key: AttributeKey[_], mgr: Streams): Unit =
		printLines(lastLines(ScopedKey(scope,key), mgr))
	def printLines(lines: Seq[String]) = lines foreach println
	def lastGrep(scope: Scope, key: AttributeKey[_], mgr: Streams, patternString: String)
	{
		val pattern = Pattern.compile(patternString)
		printLines(lastLines(ScopedKey(scope,key), mgr).flatMap(showMatches(pattern)) )
	}
	def lastLines(key: ScopedKey[_], mgr: Streams): Seq[String] =
		mgr.use(key) { s => IO.readLines(s.readText( Project.fillTaskAxis(key) )) }
}
