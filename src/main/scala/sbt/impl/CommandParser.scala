/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package sbt.impl

import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.CharSequenceReader
import scala.util.parsing.input.CharArrayReader.EofCh

/** Parses a command of the form:
* identifier argument*
* where argument may be quoted to include spaces and
* quotes and backslashes should be escaped.
* (Most of the complexity is for decent error handling.)*/
private[sbt] object CommandParser extends Parsers
{
	type Elem = Char
	def parse(commandString: String): Either[String, (String, List[String])] =
	{
		command(new CharSequenceReader(commandString.trim, 0)) match
		{
			case Success(id ~ args, next) => Right((id, args))
			case err: NoSuccess =>
			{
				val pos = err.next.pos
				Left("Could not parse command: (" + pos.line + "," + pos.column + "): " + err.msg)
			}
		}
	}
	def command = phrase(identifier ~! (argument*))
	def identifier = unquoted | err("Expected identifier")
	def argument = ( (whitespaceChar+) ~> (unquoted | quoted) )
	
	def unquoted: Parser[String] = ((unquotedChar ~! (unquotedMainChar*)) ^^ { case a ~ tail => (a :: tail).mkString("") })
	def quoted: Parser[String] = quote ~> quotedChars <~ (quote | err("Missing closing quote character"))

	def quotedChars: Parser[String] = (escape | nonescapeChar)*
	def escape: Parser[Char] = backslash ~> (escapeChar | err("Illegal escape"))
	def escapeChar: Parser[Char] = quote | backslash
	def nonescapeChar: Parser[Char] = elem("", ch => !isEscapeChar(ch) && ch != EofCh)
	def unquotedChar: Parser[Char] = elem("", ch => !isEscapeChar(ch) && !Character.isWhitespace(ch) && ch != EofCh)
	def unquotedMainChar: Parser[Char] = unquotedChar | (errorIfEscape ~> failure(""))

	private def errorIfEscape = (not(quote) | err("Unexpected quote character")) ~>
		(not(backslash) | err("Escape sequences can only occur in a quoted argument"))

	private def isEscapeChar(ch: Char) = ch == '\\' || ch == '"'
	
	def quote: Parser[Char] = '"'
	def backslash: Parser[Char] = '\\'
	def whitespaceChar: Parser[Char] = elem("whitespace", ch => Character.isWhitespace(ch))

	private implicit def toString(p: Parser[List[Char]]): Parser[String] = p ^^ {_ mkString "" }
}