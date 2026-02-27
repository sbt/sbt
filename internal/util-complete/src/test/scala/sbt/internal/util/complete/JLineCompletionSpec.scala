/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util
package complete

import org.scalacheck.*
import org.scalacheck.Prop.*
import org.scalacheck.Gen.*

object JLineCompletionSpec extends Properties("JLineCompletion"):
  private val alphaNumChars: Seq[Char] =
    ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
  private val genToken: Gen[String] = nonEmptyListOf(oneOf(alphaNumChars)).map(_.mkString)
  private val genTail: Gen[String] = listOf(oneOf(alphaNumChars)).map(_.mkString)

  private def randomCase(s: String): Gen[String] =
    sequence[Seq[Char], Char](s.toVector.map: c =>
      if c.isLetter then oneOf(c.toLower, c.toUpper)
      else const(c)).map(_.mkString)

  property("startsWithIgnoreCase accepts case variants of matching prefix") =
    forAll(genToken, genTail): (prefix, tail) =>
      forAll(randomCase(prefix)): casedPrefix =>
        JLineCompletion.startsWithIgnoreCase(prefix + tail, casedPrefix)

  property("startsWithIgnoreCase rejects longer prefixes") = forAll(genToken): token =>
    !JLineCompletion.startsWithIgnoreCase(token, token + "x")

  property("tokenBeforeCursor returns suffix after last whitespace") =
    forAll(listOf(genToken), genToken): (parts, lastToken) =>
      val beforeCursor = (parts :+ lastToken).mkString(" ")
      JLineCompletion.tokenBeforeCursor(beforeCursor) == lastToken

  property("filterCaseInsensitive matches a known command regardless of case") =
    forAll(randomCase("testo")): token =>
      val candidates = Seq("testOnly", "testQuick", "compile", "clean")
      JLineCompletion.filterCaseInsensitive(token, candidates) == Seq("testOnly")

  property("commonPrefixIgnoreCase preserves shared prefix length") = forAll(genToken): prefix =>
    val a = prefix + "Only"
    val b = prefix + "Quick"
    JLineCompletion.commonPrefixIgnoreCase(Seq(a, b)).length == prefix.length

  property("parser completor drops wrong-case insertions for issue scenario") =
    val commands = Set("testOnly", "testQuick", "compile", "clean")
    val parser = Parser.token(DefaultParsers.ID.examples(commands))
    val completor = JLineCompletion.parserAsCompletor(parser)
    val (insertWrongCase, _) = completor("testo", 1)
    val (fallbackInsert, _) = completor("", 1)
    (insertWrongCase.isEmpty) :| "strict parser insertion is empty" &&
    (JLineCompletion.filterCaseInsensitive("testo", fallbackInsert) == Seq("testOnly")) :|
      "fallback candidates recover testOnly"
end JLineCompletionSpec
