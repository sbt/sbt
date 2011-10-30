/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.util.regex.Pattern
	import java.io.File
	import Keys.{Streams, TaskStreams}
	import Project.ScopedKey
	import Load.BuildStructure
	import Aggregation.{getTasks, KeyValue}
	import Types.idFun
	import annotation.tailrec
	import scala.Console.{BOLD, RESET}

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

	def last(key: ScopedKey[_], structure: BuildStructure, streams: Streams, printLines: Seq[String] => Unit)(implicit display: Show[ScopedKey[_]]): Unit =
		printLines( flatLines(lastLines(key, structure, streams))(idFun) )

	def last(file: File, printLines: Seq[String] => Unit, tailDelim: String = DefaultTail): Unit =
		printLines(tailLines(file, tailDelim))

	def lastGrep(key: ScopedKey[_], structure: BuildStructure, streams: Streams, patternString: String, printLines: Seq[String] => Unit)(implicit display: Show[ScopedKey[_]]): Unit =
	{
		val pattern = Pattern compile patternString
		val lines = flatLines( lastLines(key, structure, streams) )(_ flatMap showMatches(pattern))
		printLines( lines )
	}
	def lastGrep(file: File, patternString: String, printLines: Seq[String] => Unit, tailDelim: String = DefaultTail): Unit =
		printLines(grep( tailLines(file, tailDelim), patternString) )
	def grep(lines: Seq[String], patternString: String): Seq[String] =
		lines flatMap showMatches(Pattern compile patternString)

	def flatLines(outputs: Seq[KeyValue[Seq[String]]])(f: Seq[String] => Seq[String])(implicit display: Show[ScopedKey[_]]): Seq[String] =
	{
		val single = outputs.size == 1
		outputs flatMap { case KeyValue(key, lines) =>
			val flines = f(lines)
			if(!single) bold(display(key)) +: flines else flines
		}
	}
	def bold(s: String) = if(ConsoleLogger.formatEnabled) BOLD + s + RESET else s

	def lastLines(key: ScopedKey[_], structure: BuildStructure, streams: Streams): Seq[KeyValue[Seq[String]]] =
	{
		val aggregated = Aggregation.getTasks(key, structure, true)
		val outputs = aggregated map { case KeyValue(key, value) => KeyValue(key, lastLines(key, streams)) }
		outputs.filterNot(_.value.isEmpty)
	}
	def lastLines(key: ScopedKey[_], mgr: Streams): Seq[String]  =	 mgr.use(key) { s => IO.readLines(s.readText( Project.fillTaskAxis(key) )) }
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
