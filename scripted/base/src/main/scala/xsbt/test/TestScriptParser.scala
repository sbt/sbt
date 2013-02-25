/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package xsbt.test

import java.io.{BufferedReader, File, InputStreamReader}
import scala.util.parsing.combinator._
import scala.util.parsing.input.Positional
import Character.isWhitespace
import sbt.IO

/*
statement*
statement ::= startChar successChar word+ nl
startChar ::= <single character>
successChar ::= '+' | '-'
word ::= [^ \[\]]+
comment ::= '#' \S* nl
nl ::= '\r' \'n' | '\n' | '\r' | eof
*/
final case class Statement(command: String, arguments: List[String], successExpected: Boolean, line: Int) extends NotNull
{
	def linePrefix = "{line " + line + "} "
}

private object TestScriptParser
{
	val SuccessLiteral = "success"
	val FailureLiteral = "failure"
	val WordRegex = """[^ \[\]\s'\"][^ \[\]\s]*""".r
}

import TestScriptParser._
class TestScriptParser(handlers: Map[Char, StatementHandler]) extends RegexParsers with NotNull
{
	require(!handlers.isEmpty)
	override def skipWhitespace = false

	import IO.read
	if(handlers.keys.exists(isWhitespace))
		sys.error("Start characters cannot be whitespace")
	if(handlers.keys.exists(key => key == '+' || key == '-'))
		sys.error("Start characters cannot be '+' or '-'")

	def parse(scriptFile: File): List[(StatementHandler, Statement)] = parse(read(scriptFile), Some(scriptFile.getCanonicalPath))
	def parse(script: String): List[(StatementHandler, Statement)] = parse(script, None)
	private def parse(script: String, label: Option[String]): List[(StatementHandler, Statement)] =
	{
		parseAll(statements, script) match
		{
			case Success(result, next) => result
			case err: NoSuccess =>
			{
				val labelString = label.map("'" + _ + "' ").getOrElse("")
				sys.error("Could not parse test script, " + labelString + err.toString)
			}
		}
	}
	
	lazy val statements = rep1(space ~> statement  <~ newline )
	def statement: Parser[ ( StatementHandler, Statement ) ] =
	{
		trait PositionalStatement extends Positional {
			def tuple: (StatementHandler, Statement )
		}
		positioned {
			val command = (word | err("expected command"))
			val arguments = rep(space ~> (word | failure("expected argument") ))
			(successParser ~ (space ~> startCharacterParser <~ space) ~! command ~! arguments)  ^^
			{
				case successExpected ~ start ~ command ~ arguments =>
					new PositionalStatement {
						def tuple = ( handlers(start), new Statement(command, arguments, successExpected, pos.line) )
					}
			}
		} ^^ (_.tuple)
	}
	def successParser: Parser[Boolean] = ( '+' ^^^ true) | ('-' ^^^ false) | success(true)
	def space: Parser[String] = """[ \t]*""".r
	lazy val word: Parser[String] =  ("\'" ~> "[^'\n\r]*".r <~ "\'")  |  ("\"" ~> "[^\"\n\r]*".r <~ "\"")  |  WordRegex
	def startCharacterParser: Parser[Char] = elem("start character", handlers.contains _) |
		( ( newline | err("expected start character " +handlers.keys.mkString("(", "", ")") ) ) ~> failure("end of input") )
		
	def newline = """\s*([\n\r]|$)""".r
}