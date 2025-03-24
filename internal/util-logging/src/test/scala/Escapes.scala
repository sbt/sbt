/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import org.scalacheck.*
import Prop.*
import Gen.{ listOf, oneOf }

import EscHelpers.{ ESC, hasEscapeSequence, isEscapeTerminator, removeEscapeSequences }

object Escapes extends Properties("Escapes") {
  property("genTerminator only generates terminators") =
    forAllNoShrink(genTerminator)((c: Char) => isEscapeTerminator(c))

  property("genWithoutTerminator only generates terminators") =
    forAllNoShrink(genWithoutTerminator) { (s: String) =>
      s.forall(c => !isEscapeTerminator(c))
    }

  property("hasEscapeSequence is false when no escape character is present") =
    forAllNoShrink(genWithoutEscape)((s: String) => !hasEscapeSequence(s))

  property("hasEscapeSequence is true when escape character is present") =
    forAllNoShrink(genWithRandomEscapes)((s: String) => hasEscapeSequence(s))

  property("removeEscapeSequences is the identity when no escape character is present") =
    forAllNoShrink(genWithoutEscape) { (s: String) =>
      val removed: String = removeEscapeSequences(s)
      ("Escape sequence removed: '" + removed + "'") |:
        (removed == s)
    }

  property("No escape characters remain after removeEscapeSequences") = forAll { (s: String) =>
    val removed: String = removeEscapeSequences(s)
    ("Escape sequence removed: '" + removed + "'") |:
      !hasEscapeSequence(removed)
  }

  private final val ecs = ESC.toString
  private val partialEscapeSequences =
    Gen.oneOf(Gen const ecs, Gen const ecs ++ "[", Gen.choose('@', '_').map(ecs :+ _))

  property("removeEscapeSequences handles partial escape sequences") =
    forAll(partialEscapeSequences) { s =>
      val removed: String = removeEscapeSequences(s)
      s"Escape sequence removed: '$removed'" |: !hasEscapeSequence(removed)
    }

  property("removeEscapeSequences returns string without escape sequences") =
    forAllNoShrink(genWithoutEscape, genEscapePairs) {
      (start: String, escapes: List[EscapeAndNot]) =>
        val withEscapes: String =
          start + escapes.map(ean => ean.escape.makeString + ean.notEscape).mkString("")
        val removed: String = removeEscapeSequences(withEscapes)
        val original = start + escapes.map(_.notEscape).mkString("")
        val diffCharString = diffIndex(original, removed)
        ("Input string    : '" + withEscapes + "'") |:
          ("Expected        : '" + original + "'") |:
          ("Escapes removed : '" + removed + "'") |:
          (diffCharString) |:
          (original == removed)
    }

  def diffIndex(expect: String, original: String): String = {
    var i = 0;
    while (i < expect.length && i < original.length) {
      if (expect.charAt(i) != original.charAt(i))
        return ("Differing character, idx: " + i + ", char: " + original.charAt(i) +
          ", expected: " + expect.charAt(i))
      i += 1
    }
    if (expect.length != original.length) return s"Strings are different lengths!"
    "No differences found"
  }

  final case class EscapeAndNot(escape: EscapeSequence, notEscape: String) {
    override def toString =
      s"EscapeAntNot(escape = [$escape], notEscape = [${notEscape.map(_.toInt)}])"
  }

  // 2.10.5 warns on "implicit numeric widening" but it looks like a bug: https://issues.scala-lang.org/browse/SI-8450
  final case class EscapeSequence(content: String, terminator: Char) {
    if (!content.isEmpty) {
      assert(
        content.tail.forall(c => !isEscapeTerminator(c)),
        "Escape sequence content contains an escape terminator: '" + content + "'"
      )
      assert(
        (content.head == '[') || !isEscapeTerminator(content.head),
        "Escape sequence content contains an escape terminator: '" + content.headOption + "'"
      )
    }
    assert(isEscapeTerminator(terminator))
    def makeString: String = s"$ESC$content$terminator"

    override def toString =
      if (content.isEmpty) s"ESC (${terminator.toInt})"
      else s"ESC ($content) (${terminator.toInt})"
  }

  private def noEscape(s: String): String = s.replace(ESC, ' ')

  lazy val genEscapeSequence: Gen[EscapeSequence] =
    oneOf(genKnownSequence, genTwoCharacterSequence, genArbitraryEscapeSequence)

  lazy val genEscapePair: Gen[EscapeAndNot] =
    for (esc <- genEscapeSequence; not <- genWithoutEscape) yield EscapeAndNot(esc, not)

  lazy val genEscapePairs: Gen[List[EscapeAndNot]] = listOf(genEscapePair)

  lazy val genArbitraryEscapeSequence: Gen[EscapeSequence] =
    for (content <- genWithoutTerminator if !content.isEmpty; term <- genTerminator)
      yield new EscapeSequence("[" + content, term)

  lazy val genKnownSequence: Gen[EscapeSequence] =
    oneOf((misc ++ setGraphicsMode ++ setMode ++ resetMode).map(toEscapeSequence))

  def toEscapeSequence(s: String): EscapeSequence = EscapeSequence(s.init, s.last)

  lazy val misc = Seq("14;23H", "5;3f", "2A", "94B", "19C", "85D", "s", "u", "2J", "K")

  lazy val setGraphicsMode: Seq[String] =
    for (txt <- 0 to 8; fg <- 30 to 37; bg <- 40 to 47)
      yield txt.toString + ";" + fg.toString + ";" + bg.toString + "m"

  lazy val resetMode = setModeLike('I')
  lazy val setMode = setModeLike('h')
  def setModeLike(term: Char): Seq[String] = (0 to 19).map(i => "=" + i.toString + term)

  lazy val genWithoutTerminator =
    genRawString.map(_.filter(c => !isEscapeTerminator(c) && (c != '[')))

  lazy val genTwoCharacterSequence =
    // 91 == [ which is the CSI escape sequence.
    oneOf((64 to 95)) filter (_ != 91) map (c => new EscapeSequence("", c.toChar))

  lazy val genTerminator: Gen[Char] = Gen.choose('@', '~')
  lazy val genWithoutEscape: Gen[String] = genRawString.map(noEscape)

  def genWithRandomEscapes: Gen[String] =
    for (ls <- listOf(genRawString); end <- genRawString)
      yield ls.mkString("", ESC.toString, ESC.toString + end)

  private def genRawString = Arbitrary.arbString.arbitrary
}
