/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package scripted

import java.io.File
import scala.util.parsing.combinator._
import scala.util.parsing.input.Positional
import Character.isWhitespace
import sbt.io.IO

/*
statement*
statement ::= startChar successChar word+ nl
startChar ::= <single character>
successChar ::= '+' | '-'
word ::= [^ \[\]]+
comment ::= '#' \S* nl
nl ::= '\r' \'n' | '\n' | '\r' | eof
 */
final case class Statement(
    command: String,
    arguments: List[String],
    successExpected: Boolean,
    line: Int
) {
  def linePrefix = "{line " + line + "} "
}

private object TestScriptParser {
  val SuccessLiteral = "success"
  val FailureLiteral = "failure"
  val WordRegex = """[^ \[\]\s'\"][^ \[\]\s]*""".r
}

import TestScriptParser._
class TestScriptParser(handlers: Map[Char, StatementHandler]) extends RegexParsers {
  require(handlers.nonEmpty)
  override def skipWhitespace = false

  import IO.read
  if (handlers.keys.exists(isWhitespace))
    sys.error("Start characters cannot be whitespace")
  if (handlers.keys.exists(key => key == '+' || key == '-'))
    sys.error("Start characters cannot be '+' or '-'")

  @deprecated("Use variant that specifies whether to strip quotes or not", "1.4.0")
  def parse(scriptFile: File): List[(StatementHandler, Statement)] =
    parse(scriptFile, stripQuotes = true)
  def parse(scriptFile: File, stripQuotes: Boolean): List[(StatementHandler, Statement)] =
    parse(read(scriptFile), Some(scriptFile.getAbsolutePath), stripQuotes)
  @deprecated("Use variant that specifies whether to strip quotes or not", "1.4.0")
  def parse(script: String): List[(StatementHandler, Statement)] =
    parse(script, None, stripQuotes = true)
  def parse(script: String, stripQuotes: Boolean): List[(StatementHandler, Statement)] =
    parse(script, None, stripQuotes)
  private def parse(
      script: String,
      label: Option[String],
      stripQuotes: Boolean
  ): List[(StatementHandler, Statement)] = {
    parseAll(statements(stripQuotes), script) match {
      case Success(result, next) => result
      case err: NoSuccess => {
        val labelString = label.map("'" + _ + "' ").getOrElse("")
        sys.error("Could not parse test script, " + labelString + err.toString)
      }
    }
  }

  @deprecated("Use variant that specifies whether to strip quotes or not", "1.4.0")
  lazy val statements = rep1(space ~> statement <~ newline)
  def statements(stripQuotes: Boolean): Parser[List[(StatementHandler, Statement)]] =
    rep1(space ~> statement(stripQuotes) <~ newline)

  @deprecated("Use variant that specifies whether to strip quotes or not", "1.4.0")
  def statement: Parser[(StatementHandler, Statement)] = statement(stripQuotes = true)
  def statement(stripQuotes: Boolean): Parser[(StatementHandler, Statement)] = {
    trait PositionalStatement extends Positional {
      def tuple: (StatementHandler, Statement)
    }
    positioned {
      val w = if (stripQuotes) word else rawWord
      val command = w | err("expected command")
      val arguments = rep(space ~> w | failure("expected argument"))
      (successParser ~ (space ~> startCharacterParser <~ space) ~! command ~! arguments) ^^ {
        case successExpected ~ start ~ command ~ arguments =>
          new PositionalStatement {
            def tuple =
              (handlers(start), new Statement(command, arguments, successExpected, pos.line))
          }
      }
    } ^^ (_.tuple)
  }

  def successParser: Parser[Boolean] = ('+' ^^^ true) | ('-' ^^^ false) | success(true)
  def space: Parser[String] = """[ \t]*""".r

  lazy val word: Parser[String] =
    ("\'" ~> "[^'\n\r]*".r <~ "\'") | "\"" ~> "[^\"\n\r]*".r <~ "\'" | WordRegex
  private lazy val rawWord: Parser[String] =
    ("\'" ~> "[^'\n\r]*".r <~ "\'") | "\"[^\"\n\r]*\"".r | WordRegex

  def startCharacterParser: Parser[Char] =
    elem("start character", handlers.contains _) |
      (
        (newline | err("expected start character " + handlers.keys.mkString("(", "", ")")))
          ~> failure("end of input")
      )

  def newline = """\s*([\n\r]|$)""".r
}
