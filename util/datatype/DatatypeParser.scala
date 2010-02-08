/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package xsbt.api

import java.io.File
import xsbt.FileUtilities
import Function.tupled
import java.util.regex.Pattern

class DatatypeParser extends NotNull
{
	val WhitespacePattern = Pattern compile """\s*"""//(?>\#(.*))?"""
	val EnumPattern = Pattern compile """enum\s+(\S+)\s*:\s*(.+)"""
	val ClassPattern = Pattern compile """(\t*)(\S+)\s*"""
	val MemberPattern = Pattern compile """(\t*)(\S+)\s*:\s*([^\s*]+)([*]?)"""

	def processWhitespaceLine(l: Array[String], line: Int) = new WhitespaceLine(l.mkString, line)
	def processEnumLine(l: Array[String], line: Int) = new EnumLine(l(0), l(1).split(",").map(_.trim), line)
	def processClassLine(l: Array[String], line: Int) = new ClassLine(l(1), l(0).length, line)
	def processMemberLine(l: Array[String], line: Int) = new MemberLine(l(1), l(2), l(3).isEmpty, l(0).length, line)

	def error(l: Line, msg: String): Nothing = error(l.line, msg)
	def error(line: Int, msg: String): Nothing = throw new RuntimeException("{line " + line + "} " + msg)

	def parseFile(file: File): Seq[Definition] =
	{
		val (open, closed) = ( (Array[ClassDef](), List[Definition]()) /: parseLines(file) ) {
			case ((open, defs), line) => processLine(open, defs, line)
		}
		open ++ closed
	}
	def parseLines(file: File): Seq[Line] = getLines(FileUtilities.read(file)).toList.zipWithIndex.map(tupled(parseLine))
	def getLines(content: String): Seq[String] = content split "(?m)$(?s:.)(?!$)*(^|\\Z)"
	def parseLine(line: String, lineNumber: Int): Line =
		matchPattern(WhitespacePattern -> processWhitespaceLine _, EnumPattern -> processEnumLine _,
			ClassPattern -> processClassLine _, MemberPattern -> processMemberLine _)(line, lineNumber)
	type Handler = (Array[String], Int) => Line
	def matchPattern(patterns: (Pattern, Handler)*)(line: String, lineNumber: Int): Line =
		patterns.flatMap { case (pattern, f) => matchPattern(pattern, f)(line, lineNumber) }.headOption.getOrElse {
			error(lineNumber, "Invalid line, expected enum, class, or member definition")
		}
	def matchPattern(pattern: Pattern, f: Handler)(line: String, lineNumber: Int): Option[Line] =
	{
		val matcher = pattern.matcher(line)
		if(matcher.matches)
		{
			val count = matcher.groupCount
			val groups = (for(i <- 1 to count) yield matcher.group(i)).toArray[String]
			Some( f(groups, lineNumber) )
		}
		else
			None
	}

	def processLine(open: Array[ClassDef], definitions: List[Definition], line: Line): (Array[ClassDef], List[Definition]) =
	{
		line match
		{
			case w: WhitespaceLine => (open, definitions)
			case e: EnumLine => (Array(), new EnumDef(e.name, e.members) :: open.toList ::: definitions)
			case m: MemberLine =>
				if(m.level == 0 || m.level > open.length) error(m, "Member must be declared in a class definition")
				else withCurrent(open, definitions, m.level) { c => List( c + m) }
			case c: ClassLine =>
				if(c.level == 0) (Array( new ClassDef(c.name, None, Nil) ), open.toList ::: definitions)
				else if(c.level > open.length) error(c, "Class must be declared as top level or as a subclass")
				else withCurrent(open, definitions, c.level) { p => p :: new ClassDef(c.name, Some(p), Nil) :: Nil}
		}
	}
	private def withCurrent(open: Array[ClassDef], definitions: List[Definition], level: Int)(onCurrent: ClassDef => Seq[ClassDef]): (Array[ClassDef], List[Definition]) =
	{
		require(0 < level && level <= open.length)
		val closed = open.drop(level).toList
		val newOpen = open.take(level - 1) ++ onCurrent(open(level - 1))
		( newOpen.toArray, closed ::: definitions )
	}
}
