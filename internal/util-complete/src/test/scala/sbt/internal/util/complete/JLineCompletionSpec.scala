/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util
package complete

class JLineCompletionSpec extends UnitSpec:

  "tokenBeforeCursor" should "return the final token when no whitespace exists" in {
    JLineCompletion.tokenBeforeCursor("testo") shouldEqual "testo"
  }

  it should "return the token after the last whitespace" in {
    JLineCompletion.tokenBeforeCursor("root / testo") shouldEqual "testo"
  }

  it should "return empty when cursor is right after whitespace" in {
    JLineCompletion.tokenBeforeCursor("test ") shouldEqual ""
  }

  it should "handle empty input" in {
    JLineCompletion.tokenBeforeCursor("") shouldEqual ""
  }

  "startsWithIgnoreCase" should "match when prefix differs only in case" in {
    JLineCompletion.startsWithIgnoreCase("testOnly", "testo") shouldBe true
  }

  it should "match when prefix case is fully uppercase" in {
    JLineCompletion.startsWithIgnoreCase("compile", "COMP") shouldBe true
  }

  it should "not match when prefix is entirely different" in {
    JLineCompletion.startsWithIgnoreCase("compile", "test") shouldBe false
  }

  it should "match when prefix and value case already agree" in {
    JLineCompletion.startsWithIgnoreCase("testOnly", "testO") shouldBe true
  }

  "filterCaseInsensitive" should "find candidates matching token regardless of case" in {
    val candidates = Seq("testOnly", "testQuick", "compile", "clean")
    JLineCompletion.filterCaseInsensitive("testo", candidates) shouldEqual Seq("testOnly")
  }

  it should "return multiple candidates when several match" in {
    val candidates = Seq("testOnly", "testQuick", "compile")
    val result = JLineCompletion.filterCaseInsensitive("test", candidates)
    result should contain theSameElementsAs Seq("testOnly", "testQuick")
  }

  it should "return empty when nothing matches" in {
    JLineCompletion.filterCaseInsensitive("xyz", Seq("testOnly", "compile")) shouldBe empty
  }

  "commonPrefixIgnoreCase" should "find shared prefix across case-differing candidates" in {
    JLineCompletion.commonPrefixIgnoreCase("testOnly", "testQuick") shouldEqual "test"
  }

  it should "return full string when single candidate" in {
    JLineCompletion.commonPrefixIgnoreCase(Seq("testOnly")) shouldEqual "testOnly"
  }

  it should "handle candidates with identical case-insensitive prefixes" in {
    JLineCompletion.commonPrefixIgnoreCase(Seq("TestOnly", "testQuick")) shouldEqual "Test"
  }

  it should "return empty for empty input" in {
    JLineCompletion.commonPrefixIgnoreCase(Seq.empty) shouldEqual ""
  }

  "case-insensitive completion via parser" should "produce completions for correct case" in {
    val commands = Set("testOnly", "testQuick", "compile", "clean")
    val parser = Parser.token(DefaultParsers.ID.examples(commands))
    val completor = JLineCompletion.parserAsCompletor(parser)

    val (insertCorrect, _) = completor("test", 1)
    assert(insertCorrect.nonEmpty, "expected completions for correctly-cased prefix")

    val (insertWrongCase, _) = completor("testo", 1)
    assert(insertWrongCase.isEmpty, "expected no completions for wrong-cased prefix")
  }

  it should "allow fallback to find candidates from prefix completions" in {
    val commands = Set("testOnly", "testQuick", "compile", "clean")
    val parser = Parser.token(DefaultParsers.ID.examples(commands))
    val completor = JLineCompletion.parserAsCompletor(parser)
    val completions: String => (Seq[String], Seq[String]) =
      str => completor(str, 1)

    val (fallbackInsert, _) = completions("")
    val candidates = JLineCompletion.filterCaseInsensitive("testo", fallbackInsert)
    candidates shouldEqual Seq("testOnly")
  }

  it should "find multiple candidates when prefix matches several commands" in {
    val commands = Set("testOnly", "testQuick", "compile", "clean")
    val parser = Parser.token(DefaultParsers.ID.examples(commands))
    val completor = JLineCompletion.parserAsCompletor(parser)
    val completions: String => (Seq[String], Seq[String]) =
      str => completor(str, 1)

    val (fallbackInsert, _) = completions("")
    val candidates = JLineCompletion.filterCaseInsensitive("TEST", fallbackInsert)
    candidates should contain theSameElementsAs Seq("testOnly", "testQuick")
    JLineCompletion.commonPrefixIgnoreCase(candidates) shouldEqual "test"
  }

  it should "handle scoped input by using prefix before the mistyped token" in {
    val keys = Set("compile", "testOnly", "clean")
    val scopeParser =
      Parser.token(DefaultParsers.ID) ~
        Parser.token(DefaultParsers.OptSpace ~ '/' ~ DefaultParsers.OptSpace) ~
        Parser.token(DefaultParsers.ID.examples(keys))
    val completor = JLineCompletion.parserAsCompletor(scopeParser)
    val completions: String => (Seq[String], Seq[String]) =
      str => completor(str, 1)

    val (prefixInsert, _) = completions("root / ")
    val candidates = JLineCompletion.filterCaseInsensitive("testo", prefixInsert)
    candidates shouldEqual Seq("testOnly")
  }

end JLineCompletionSpec
