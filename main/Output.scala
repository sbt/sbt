/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.util.regex.Pattern
	import java.io.File
	import Keys.{Streams, TaskStreams}
	import Project.ScopedKey
	import Aggregation.{KeyValue, Values}
	import Types.idFun
	import Highlight.{bold, showMatches}
	import annotation.tailrec

object Output
{
	final val DefaultTail = "> "

	def last(keys: Values[_], streams: Streams, printLines: Seq[String] => Unit)(implicit display: Show[ScopedKey[_]]): Unit =
		printLines( flatLines(lastLines(keys, streams))(idFun) )

	def last(file: File, printLines: Seq[String] => Unit, tailDelim: String = DefaultTail): Unit =
		printLines(tailLines(file, tailDelim))

	def lastGrep(keys: Values[_], streams: Streams, patternString: String, printLines: Seq[String] => Unit)(implicit display: Show[ScopedKey[_]]): Unit =
	{
		val pattern = Pattern compile patternString
		val lines = flatLines( lastLines(keys, streams) )(_ flatMap showMatches(pattern))
		printLines( lines )
	}
	def lastGrep(file: File, patternString: String, printLines: Seq[String] => Unit, tailDelim: String = DefaultTail): Unit =
		printLines(grep( tailLines(file, tailDelim), patternString) )
	def grep(lines: Seq[String], patternString: String): Seq[String] =
		lines flatMap showMatches(Pattern compile patternString)

	def flatLines(outputs: Values[Seq[String]])(f: Seq[String] => Seq[String])(implicit display: Show[ScopedKey[_]]): Seq[String] =
	{
		val single = outputs.size == 1
		outputs flatMap { case KeyValue(key, lines) =>
			val flines = f(lines)
			if(!single) bold(display(key)) +: flines else flines
		}
	}

	def lastLines(keys: Values[_], streams: Streams): Values[Seq[String]] =
	{
		val outputs = keys map { case KeyValue(key, value) => KeyValue(key, lastLines(key, streams)) }
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
