/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package complete

import Parser._
import java.io.File
import java.net.URI
import java.lang.Character.{
  getType,
  MATH_SYMBOL,
  OTHER_SYMBOL,
  DASH_PUNCTUATION,
  OTHER_PUNCTUATION,
  MODIFIER_SYMBOL,
  CURRENCY_SYMBOL
}

/** Provides standard implementations of commonly useful [[Parser]]s. */
trait Parsers {

  /** Matches the end of input, providing no useful result on success. */
  lazy val EOF = not(any, "Expected EOF")

  /** Parses any single character and provides that character as the result. */
  lazy val any: Parser[Char] = charClass(_ => true, "any character")

  /** Set that contains each digit in a String representation.*/
  lazy val DigitSet = Set("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

  /** Parses any single digit and provides that digit as a Char as the result.*/
  lazy val Digit = charClass(_.isDigit, "digit") examples DigitSet

  /** Set containing Chars for hexadecimal digits 0-9 and A-F (but not a-f). */
  lazy val HexDigitSet =
    Set('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

  /** Parses a single hexadecimal digit (0-9, a-f, A-F). */
  lazy val HexDigit = charClass(c => HexDigitSet(c.toUpper), "hex digit") examples HexDigitSet.map(
    _.toString)

  /** Parses a single letter, according to Char.isLetter, into a Char. */
  lazy val Letter = charClass(_.isLetter, "letter")

  /** Parses a single letter, according to Char.isUpper, into a Char. */
  lazy val Upper = charClass(_.isUpper, "upper")

  /** Parses a single letter, according to Char.isLower, into a Char. */
  lazy val Lower = charClass(_.isLower, "lower")

  /** Parses the first Char in an sbt identifier, which must be a [[Letter]].*/
  def IDStart = Letter

  /** Parses an identifier Char other than the first character.  This includes letters, digits, dash `-`, and underscore `_`.*/
  lazy val IDChar = charClass(isIDChar, "ID character")

  /** Parses an identifier String, which must start with [[IDStart]] and contain zero or more [[IDChar]]s after that. */
  lazy val ID = identifier(IDStart, IDChar)

  /** Parses a single operator Char, as allowed by [[isOpChar]]. */
  lazy val OpChar = charClass(isOpChar, "symbol")

  /** Parses a non-empty operator String, which consists only of characters allowed by [[OpChar]]. */
  lazy val Op = OpChar.+.string

  /** Parses either an operator String defined by [[Op]] or a non-symbolic identifier defined by [[ID]]. */
  lazy val OpOrID = ID | Op

  /** Parses a single, non-symbolic Scala identifier Char.  Valid characters are letters, digits, and the underscore character `_`. */
  lazy val ScalaIDChar = charClass(isScalaIDChar, "Scala identifier character")

  /** Parses a non-symbolic Scala-like identifier.  The identifier must start with [[IDStart]] and contain zero or more [[ScalaIDChar]]s after that.*/
  lazy val ScalaID = identifier(IDStart, ScalaIDChar)

  /** Parses a non-symbolic Scala-like identifier.  The identifier must start with [[Upper]] and contain zero or more [[ScalaIDChar]]s after that.*/
  lazy val CapitalizedID = identifier(Upper, ScalaIDChar)

  /** Parses a String that starts with `start` and is followed by zero or more characters parsed by `rep`.*/
  def identifier(start: Parser[Char], rep: Parser[Char]): Parser[String] =
    start ~ rep.* map { case x ~ xs => (x +: xs).mkString }

  def opOrIDSpaced(s: String): Parser[Char] =
    if (DefaultParsers.matches(ID, s))
      OpChar | SpaceClass
    else if (DefaultParsers.matches(Op, s))
      IDChar | SpaceClass
    else
      any

  /** Returns true if `c` an operator character. */
  def isOpChar(c: Char) = !isDelimiter(c) && isOpType(getType(c))

  def isOpType(cat: Int) = cat match {
    case MATH_SYMBOL | OTHER_SYMBOL | DASH_PUNCTUATION | OTHER_PUNCTUATION | MODIFIER_SYMBOL |
        CURRENCY_SYMBOL =>
      true; case _      => false
  }

  /** Returns true if `c` is a dash `-`, a letter, digit, or an underscore `_`. */
  def isIDChar(c: Char) = isScalaIDChar(c) || c == '-'

  /** Returns true if `c` is a letter, digit, or an underscore `_`. */
  def isScalaIDChar(c: Char) = c.isLetterOrDigit || c == '_'

  def isDelimiter(c: Char) = c match {
    case '`' | '\'' | '\"' | /*';' | */ ',' | '.' => true; case _ => false
  }

  /** Matches a single character that is not a whitespace character. */
  lazy val NotSpaceClass = charClass(!_.isWhitespace, "non-whitespace character")

  /** Matches a single whitespace character, as determined by Char.isWhitespace.*/
  lazy val SpaceClass = charClass(_.isWhitespace, "whitespace character")

  /** Matches a non-empty String consisting of non-whitespace characters. */
  lazy val NotSpace = NotSpaceClass.+.string

  /** Matches a possibly empty String consisting of non-whitespace characters. */
  lazy val OptNotSpace = NotSpaceClass.*.string

  /**
   * Matches a non-empty String consisting of whitespace characters.
   * The suggested tab completion is a single, constant space character.
   */
  lazy val Space = SpaceClass.+.examples(" ")

  /**
   * Matches a possibly empty String consisting of whitespace characters.
   * The suggested tab completion is a single, constant space character.
   */
  lazy val OptSpace = SpaceClass.*.examples(" ")

  /** Parses a non-empty String that contains only valid URI characters, as defined by [[URIChar]].*/
  lazy val URIClass = URIChar.+.string !!! "Invalid URI"

  /** Triple-quotes, as used for verbatim quoting.*/
  lazy val VerbatimDQuotes = "\"\"\""

  /** Double quote character. */
  lazy val DQuoteChar = '\"'

  /** Backslash character. */
  lazy val BackslashChar = '\\'

  /** Matches a single double quote. */
  lazy val DQuoteClass = charClass(_ == DQuoteChar, "double-quote character")

  /** Matches any character except a double quote or whitespace. */
  lazy val NotDQuoteSpaceClass =
    charClass({ c: Char =>
      (c != DQuoteChar) && !c.isWhitespace
    }, "non-double-quote-space character")

  /** Matches any character except a double quote or backslash. */
  lazy val NotDQuoteBackslashClass =
    charClass({ c: Char =>
      (c != DQuoteChar) && (c != BackslashChar)
    }, "non-double-quote-backslash character")

  /** Matches a single character that is valid somewhere in a URI. */
  lazy val URIChar = charClass(alphanum) | chars("_-!.~'()*,;:$&+=?/[]@%#")

  /** Returns true if `c` is an ASCII letter or digit. */
  def alphanum(c: Char) =
    ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9')

  /**
   * @param base the directory used for completion proposals (when the user presses the TAB key). Only paths under this
   *             directory will be proposed.
   * @return the file that was parsed from the input string. The returned path may or may not exist.
   */
  def fileParser(base: File): Parser[File] =
    OptSpace ~> StringBasic
      .examples(new FileExamples(base))
      .map(new File(_))

  /** Parses a port number.  Currently, this accepts any integer and presents a tab completion suggestion of `<port>`. */
  lazy val Port = token(IntBasic, "<port>")

  /** Parses a signed integer. */
  lazy val IntBasic = mapOrFail('-'.? ~ Digit.+)(Function.tupled(toInt))

  /** Parses an unsigned integer. */
  lazy val NatBasic = mapOrFail(Digit.+)(_.mkString.toInt)

  private[this] def toInt(neg: Option[Char], digits: Seq[Char]): Int =
    (neg.toSeq ++ digits).mkString.toInt

  /** Parses the lower-case values `true` and `false` into their respesct Boolean values.  */
  lazy val Bool = ("true" ^^^ true) | ("false" ^^^ false)

  /**
   * Parses a potentially quoted String value.  The value may be verbatim quoted ([[StringVerbatim]]),
   * quoted with interpreted escapes ([[StringEscapable]]), or unquoted ([[NotQuoted]]).
   */
  lazy val StringBasic = StringVerbatim | StringEscapable | NotQuoted

  /**
   * Parses a verbatim quoted String value, discarding the quotes in the result.  This kind of quoted text starts with triple quotes `"""`
   * and ends at the next triple quotes and may contain any character in between.
   */
  lazy val StringVerbatim: Parser[String] = VerbatimDQuotes ~>
    any.+.string.filter(!_.contains(VerbatimDQuotes), _ => "Invalid verbatim string") <~
    VerbatimDQuotes

  /**
   * Parses a string value, interpreting escapes and discarding the surrounding quotes in the result.
   * See [[EscapeSequence]] for supported escapes.
   */
  lazy val StringEscapable: Parser[String] =
    (DQuoteChar ~> (NotDQuoteBackslashClass | EscapeSequence).+.string <~ DQuoteChar |
      (DQuoteChar ~ DQuoteChar) ^^^ "")

  /**
   * Parses a single escape sequence into the represented Char.
   * Escapes start with a backslash and are followed by `u` for a [[UnicodeEscape]] or by `b`, `t`, `n`, `f`, `r`, `"`, `'`, `\` for standard escapes.
   */
  lazy val EscapeSequence: Parser[Char] =
    BackslashChar ~> ('b' ^^^ '\b' | 't' ^^^ '\t' | 'n' ^^^ '\n' | 'f' ^^^ '\f' | 'r' ^^^ '\r' |
      '\"' ^^^ '\"' | '\'' ^^^ '\'' | '\\' ^^^ '\\' | UnicodeEscape)

  /**
   * Parses a single unicode escape sequence into the represented Char.
   * A unicode escape begins with a backslash, followed by a `u` and 4 hexadecimal digits representing the unicode value.
   */
  lazy val UnicodeEscape: Parser[Char] =
    ("u" ~> repeat(HexDigit, 4, 4)) map { seq =>
      Integer.parseInt(seq.mkString, 16).toChar
    }

  /** Parses an unquoted, non-empty String value that cannot start with a double quote and cannot contain whitespace.*/
  lazy val NotQuoted = (NotDQuoteSpaceClass ~ OptNotSpace) map { case (c, s) => c.toString + s }

  /**
   * Applies `rep` zero or more times, separated by `sep`.
   * The result is the (possibly empty) sequence of results from the multiple `rep` applications.  The `sep` results are discarded.
   */
  def repsep[T](rep: Parser[T], sep: Parser[_]): Parser[Seq[T]] =
    rep1sep(rep, sep) ?? Nil

  /**
   * Applies `rep` one or more times, separated by `sep`.
   * The result is the non-empty sequence of results from the multiple `rep` applications.  The `sep` results are discarded.
   */
  def rep1sep[T](rep: Parser[T], sep: Parser[_]): Parser[Seq[T]] =
    (rep ~ (sep ~> rep).*).map { case (x ~ xs) => x +: xs }

  /** Wraps the result of `p` in `Some`.*/
  def some[T](p: Parser[T]): Parser[Option[T]] = p map { v =>
    Some(v)
  }

  /**
   * Applies `f` to the result of `p`, transforming any exception when evaluating
   * `f` into a parse failure with the exception `toString` as the message.
   */
  def mapOrFail[S, T](p: Parser[S])(f: S => T): Parser[T] =
    p flatMap { s =>
      try { success(f(s)) } catch { case e: Exception => failure(e.toString) }
    }

  /**
   * Parses a space-delimited, possibly empty sequence of arguments.
   * The arguments may use quotes and escapes according to [[StringBasic]].
   */
  def spaceDelimited(display: String): Parser[Seq[String]] =
    (token(Space) ~> token(StringBasic, display)).* <~ SpaceClass.*

  /** Applies `p` and uses `true` as the result if it succeeds and turns failure into a result of `false`. */
  def flag[T](p: Parser[T]): Parser[Boolean] = (p ^^^ true) ?? false

  /**
   * Defines a sequence parser where the parser used for each part depends on the previously parsed values.
   * `p` is applied to the (possibly empty) sequence of already parsed values to obtain the next parser to use.
   * The parsers obtained in this way are separated by `sep`, whose result is discarded and only the sequence
   * of values from the parsers returned by `p` is used for the result.
   */
  def repeatDep[A](p: Seq[A] => Parser[A], sep: Parser[Any]): Parser[Seq[A]] = {
    def loop(acc: Seq[A]): Parser[Seq[A]] = {
      val next = (sep ~> p(acc)) flatMap { result =>
        loop(acc :+ result)
      }
      next ?? acc
    }
    p(Vector()) flatMap { first =>
      loop(Seq(first))
    }
  }

  /** Applies String.trim to the result of `p`. */
  def trimmed(p: Parser[String]) = p map { _.trim }

  /** Parses a URI that is valid according to the single argument java.net.URI constructor. */
  lazy val basicUri = mapOrFail(URIClass)(uri => new URI(uri))

  /** Parses a URI that is valid according to the single argument java.net.URI constructor, using `ex` as tab completion examples. */
  def Uri(ex: Set[URI]) = basicUri examples (ex.map(_.toString))
}

/** Provides standard [[Parser]] implementations. */
object Parsers extends Parsers

/** Provides common [[Parser]] implementations and helper methods.*/
object DefaultParsers extends Parsers with ParserMain {

  /** Applies parser `p` to input `s` and returns `true` if the parse was successful. */
  def matches(p: Parser[_], s: String): Boolean =
    apply(p)(s).resultEmpty.isValid

  /** Returns `true` if `s` parses successfully according to [[ID]].*/
  def validID(s: String): Boolean = matches(ID, s)

}
