/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt.complete

	import Parser._
	import java.io.File
	import java.net.URI
	import java.lang.Character.{getType, MATH_SYMBOL, OTHER_SYMBOL, DASH_PUNCTUATION, OTHER_PUNCTUATION, MODIFIER_SYMBOL, CURRENCY_SYMBOL}

// Some predefined parsers
trait Parsers
{
	lazy val EOF = not(any)

	lazy val any: Parser[Char] = charClass(_ => true, "any character")

	lazy val DigitSet = Set("0","1","2","3","4","5","6","7","8","9")
	lazy val Digit = charClass(_.isDigit, "digit") examples DigitSet
	lazy val OctalDigitSet = Set("0","1","2","3","4","5","6","7")
	lazy val OctalDigit = charClass(c => OctalDigitSet(c.toString), "octal") examples OctalDigitSet
	lazy val HexDigitSet = Set("0","1","2","3","4","5","6","7","8","9", "A", "B", "C", "D", "E", "F")
	lazy val HexDigit = charClass(c => HexDigitSet(c.toString.toUpperCase), "hex") examples HexDigitSet
	lazy val Letter = charClass(_.isLetter, "letter")
	def IDStart = Letter
	lazy val IDChar = charClass(isIDChar, "ID character")
	lazy val ID = IDStart ~ IDChar.* map { case x ~ xs => (x +: xs).mkString }
	lazy val OpChar = charClass(isOpChar, "symbol")
	lazy val Op = OpChar.+.string
	lazy val OpOrID = ID | Op

	def opOrIDSpaced(s: String): Parser[Char] =
		if(DefaultParsers.matches(ID, s))
			OpChar | SpaceClass
		else if(DefaultParsers.matches(Op, s))
			IDChar | SpaceClass
		else
			any

	def isOpChar(c: Char) = !isDelimiter(c) && isOpType(getType(c))
	def isOpType(cat: Int) = cat match { case MATH_SYMBOL | OTHER_SYMBOL | DASH_PUNCTUATION | OTHER_PUNCTUATION | MODIFIER_SYMBOL | CURRENCY_SYMBOL => true; case _ => false }
	def isIDChar(c: Char) = c.isLetterOrDigit || c == '-' || c == '_'
	def isDelimiter(c: Char) = c match { case '`' | '\'' | '\"' | /*';' | */',' | '.' => true ; case _ => false }
	
	lazy val NotSpaceClass = charClass(!_.isWhitespace, "non-whitespace character")
	lazy val SpaceClass = charClass(_.isWhitespace, "whitespace character")
	lazy val NotSpace = NotSpaceClass.+.string
	lazy val Space = SpaceClass.+.examples(" ")
	lazy val OptSpace = SpaceClass.*.examples(" ")
	lazy val URIClass = URIChar.+.string !!! "Invalid URI"
	lazy val VerbatimDQuotes = "\"\"\""
	lazy val DQuoteChar = '\"'
	lazy val DQuoteClass = charClass(_ == DQuoteChar, "double-quote character")
	lazy val NotDQuoteClass = charClass(_ != DQuoteChar, "non-double-quote character")
	lazy val NotDQuoteBackslashClass = charClass({ c: Char =>
		c != DQuoteChar && c != '\\' }, "non-double-quote character")

	lazy val URIChar = charClass(alphanum) | chars("_-!.~'()*,;:$&+=?/[]@%#")
	def alphanum(c: Char) = ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9')

	// TODO: implement
	def fileParser(base: File): Parser[File] = token(mapOrFail(NotSpace)(s => new File(s.mkString)), "<file>")
	
	lazy val Port = token(IntBasic, "<port>")
	lazy val IntBasic = mapOrFail( '-'.? ~ Digit.+ )( Function.tupled(toInt) )
	lazy val NatBasic = mapOrFail( Digit.+ )( _.mkString.toInt )
	private[this] def toInt(neg: Option[Char], digits: Seq[Char]): Int =
		(neg.toSeq ++ digits).mkString.toInt
	lazy val Bool = ("true" ^^^ true) | ("false" ^^^ false)
	lazy val StringBasic = StringVerbatim | StringEscapable | NotQuoted
	def StringVerbatim: Parser[String] = {
		var dqcount = 0
		val p = VerbatimDQuotes ~
			charClass(_ match {
				case DQuoteChar =>
				  dqcount += 1
				  dqcount < 3
				case _ =>
					dqcount = 0
					true
			}).*.string ~ DQuoteChar
		p map { case ((s, p), c) => s + p + c.toString } filter(
			{ _.endsWith(VerbatimDQuotes) }, _ => "Expected '%s'" format VerbatimDQuotes) map { s =>
			s.substring(3, s.length - 3) }
	}
	lazy val StringEscapable: Parser[String] = {
		val p = DQuoteChar ~>
		  (EscapeSequence | NotDQuoteBackslashClass map {_.toString}).* <~ DQuoteChar
		p map { _.mkString }
	}
	lazy val EscapeSequence: Parser[String] =
	  "\\" ~> ("b" ^^^ "\b" | "t" ^^^ "\t" | "n" ^^^ "\n" | "f" ^^^ "\f" | "r" ^^^ "\r" |
	  "\"" ^^^ "\"" | "'" ^^^ "\'" | "\\" ^^^ "\\" | OctalEscape | UnicodeEscape)
	lazy val OctalEscape: Parser[String] =
	  repeat(OctalDigit, 1, 3) map { seq =>
	  	Integer.parseInt(seq.mkString, 8).asInstanceOf[Char].toString
	  }
	lazy val UnicodeEscape: Parser[String] =
	  ("u" ~> repeat(HexDigit, 4, 4)) map { seq =>
	  	Integer.parseInt(seq.mkString, 16).asInstanceOf[Char].toString
	  }
	lazy val NotQuoted = (NotDQuoteClass ~ NotSpace) map { case (c, s) => c.toString + s }

	def repsep[T](rep: Parser[T], sep: Parser[_]): Parser[Seq[T]] =
		rep1sep(rep, sep) ?? Nil
	def rep1sep[T](rep: Parser[T], sep: Parser[_]): Parser[Seq[T]] =
		(rep ~ (sep ~> rep).*).map { case (x ~ xs) => x +: xs }

	def some[T](p: Parser[T]): Parser[Option[T]] = p map { v => Some(v) }
	def mapOrFail[S,T](p: Parser[S])(f: S => T): Parser[T] =
		p flatMap { s => try { success(f(s)) } catch { case e: Exception => failure(e.toString) } }

	def spaceDelimited(display: String): Parser[Seq[String]] = (token(Space) ~> token(StringBasic, display)).* <~ SpaceClass.*

	def flag[T](p: Parser[T]): Parser[Boolean] = (p ^^^ true) ?? false

	def trimmed(p: Parser[String]) = p map { _.trim }
	def Uri(ex: Set[URI]) = mapOrFail(URIClass)( uri => new URI(uri)) examples(ex.map(_.toString))
}
object Parsers extends Parsers
object DefaultParsers extends Parsers with ParserMain
{
	def matches(p: Parser[_], s: String): Boolean =
		apply(p)(s).resultEmpty.isValid
	def validID(s: String): Boolean = matches(ID, s)
}