/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.util.regex.Pattern
	import java.io.File
	import Keys.{Streams, TaskStreams}
	import Project.ScopedKey
	import annotation.tailrec

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
	final val DefaultTail = "> "

	def last(key: ScopedKey[_], mgr: Streams): Unit  =  printLines(lastLines(key, mgr))
	def last(file: File, tailDelim: String = DefaultTail): Unit  =  printLines(tailLines(file, tailDelim))

	def lastGrep(key: ScopedKey[_], mgr: Streams, patternString: String): Unit =
		lastGrep(lastLines(key, mgr), patternString )
	def lastGrep(file: File, patternString: String, tailDelim: String = DefaultTail): Unit =
		lastGrep( tailLines(file, tailDelim), patternString)
	def lastGrep(lines: Seq[String], patternString: String): Unit =
		printLines(lines flatMap showMatches(Pattern compile patternString))

	def printLines(lines: Seq[String]) = lines foreach println
	def lastLines(key: ScopedKey[_], mgr: Streams): Seq[String]  =  mgr.use(key) { s => IO.readLines(s.readText( Project.fillTaskAxis(key) )) }
	def tailLines(file: File, tailDelim: String): Seq[String]  =  headLines(IO.readLines(file).reverse, tailDelim).reverse
	@tailrec def headLines(lines: Seq[String], tailDelim: String): Seq[String] =
		if(lines.isEmpty)
			lines
		else
		{
			val (first, tail) = lines.span { line => ! (line startsWith tailDelim) }
			if(first.isEmpty) headLines(tail drop 1, tailDelim) else first
		}
}
