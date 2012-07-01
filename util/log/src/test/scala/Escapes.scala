package sbt

import org.scalacheck._
import Prop._
import Gen.{listOf, oneOf}

import ConsoleLogger.{ESC, hasEscapeSequence, isEscapeTerminator, removeEscapeSequences}

object Escapes extends Properties("Escapes")
{
	property("genTerminator only generates terminators") = 
		forAllNoShrink(genTerminator) { (c: Char) => isEscapeTerminator(c) }

	property("genWithoutTerminator only generates terminators") = 
		forAllNoShrink(genWithoutTerminator) { (s: String) =>
			s.forall { c => !isEscapeTerminator(c) }
		}

	property("hasEscapeSequence is false when no escape character is present") = forAllNoShrink(genWithoutEscape) { (s: String) =>
		!hasEscapeSequence(s)
	}

	property("hasEscapeSequence is true when escape character is present") = forAllNoShrink(genWithRandomEscapes) { (s: String) =>
		hasEscapeSequence(s)
	}

	property("removeEscapeSequences is the identity when no escape character is present") = forAllNoShrink(genWithoutEscape) { (s: String) =>
		val removed: String = removeEscapeSequences(s)
		("Escape sequence removed: '" + removed + "'") |:
		(removed == s)
	}

	property("No escape characters remain after removeEscapeSequences") = forAll { (s: String) =>
		val removed: String = removeEscapeSequences(s)
		("Escape sequence removed: '" + removed + "'") |:
		!hasEscapeSequence(removed)
	}

	property("removeEscapeSequences returns string without escape sequences") = 
		forAllNoShrink( genWithoutEscape, genEscapePairs ) { (start: String, escapes: List[EscapeAndNot]) =>
			val withEscapes: String =  start + escapes.map { ean => ean.escape.makeString + ean.notEscape }
			val removed: String = removeEscapeSequences(withEscapes)
			val original = start + escapes.map(_.notEscape)
			("Input string with escapes: '" + withEscapes + "'") |:
			("Escapes removed '" + removed + "'") |:
			(original == removed)
		}

	final case class EscapeAndNot(escape: EscapeSequence, notEscape: String)
	final case class EscapeSequence(content: String, terminator: Char)
	{
		assert( content.forall(c => !isEscapeTerminator(c) ), "Escape sequence content contains an escape terminator: '" + content + "'" )
		assert( isEscapeTerminator(terminator) )
		def makeString: String = ESC + content + terminator
	}
	private[this] def noEscape(s: String): String = s.replace(ESC, ' ')

	lazy val genEscapeSequence: Gen[EscapeSequence] = oneOf(genKnownSequence, genArbitraryEscapeSequence)
	lazy val genEscapePair: Gen[EscapeAndNot] = for(esc <- genEscapeSequence; not <- genWithoutEscape) yield EscapeAndNot(esc, not)
	lazy val genEscapePairs: Gen[List[EscapeAndNot]] = listOf(genEscapePair)

	lazy val genArbitraryEscapeSequence: Gen[EscapeSequence] = 
		for(content <- genWithoutTerminator; term <- genTerminator) yield
			new EscapeSequence(content, term)
			
	lazy val genKnownSequence: Gen[EscapeSequence] =
		oneOf((misc ++ setGraphicsMode ++ setMode ++ resetMode).map(toEscapeSequence))
	
	def toEscapeSequence(s: String): EscapeSequence = EscapeSequence(s.init, s.last)

	lazy val misc = Seq("14;23H", "5;3f", "2A", "94B", "19C", "85D", "s", "u", "2J", "K")

	lazy val setGraphicsMode: Seq[String] =
		for(txt <- 0 to 8; fg <- 30 to 37; bg <- 40 to 47) yield
			txt.toString + ";" + fg.toString + ";" + bg.toString + "m"

	lazy val resetMode = setModeLike('I')
	lazy val setMode = setModeLike('h')
	def setModeLike(term: Char): Seq[String] = (0 to 19).map(i => "=" + i.toString + term)
		
	lazy val genWithoutTerminator = genRawString.map( _.filter { c => !isEscapeTerminator(c) } )

	lazy val genTerminator: Gen[Char] = Gen.choose('@', '~')
	lazy val genWithoutEscape: Gen[String] = genRawString.map(noEscape)

	def genWithRandomEscapes: Gen[String] =
		for(ls <- listOf(genRawString); end <- genRawString) yield 
			ls.mkString("", ESC.toString, ESC.toString + end)

	private def genRawString = Arbitrary.arbString.arbitrary
}
