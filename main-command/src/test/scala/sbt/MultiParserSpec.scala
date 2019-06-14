/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import org.scalatest.{ FlatSpec, Matchers }
import sbt.internal.util.complete.Parser

object MultiParserSpec {
  val parser: Parser[Seq[String]] = BasicCommands.multiParserImpl(None)
  implicit class StringOps(val s: String) {
    def parse: Seq[String] = Parser.parse(s, parser).right.get
    def parseEither: Either[String, Seq[String]] = Parser.parse(s, parser)
  }
}
import sbt.MultiParserSpec._
class MultiParserSpec extends FlatSpec with Matchers {
  "parsing" should "parse single commands" in {
    ";foo".parse shouldBe Seq("foo")
    ";   foo".parse shouldBe Seq("foo")
  }
  it should "parse multiple commands" in {
    ";foo;bar".parse shouldBe Seq("foo", "bar")
  }
  it should "parse single command with leading spaces" in {
    ";     foo".parse shouldBe Seq("foo")
  }
  it should "parse multiple commands with leading spaces" in {
    ";     foo;bar".parse shouldBe Seq("foo", "bar")
    ";     foo;    bar".parse shouldBe Seq("foo", "bar")
    ";foo; bar".parse shouldBe Seq("foo", "bar")
  }
  it should "parse command with string literal" in {
    "; foo \"barbaz\"".parse shouldBe Seq("foo \"barbaz\"")
    "; foo \"bar;baz\"".parse shouldBe Seq("foo \"bar;baz\"")
    "; foo \"barbaz\"; bar".parse shouldBe Seq("foo \"barbaz\"", "bar")
    "; foo \"barbaz\"; bar \"blah\"".parse shouldBe Seq("foo \"barbaz\"", "bar \"blah\"")
    "; foo \"bar;baz\"; bar".parse shouldBe Seq("foo \"bar;baz\"", "bar")
    "; foo \"bar;baz\"; bar \"buzz\"".parse shouldBe Seq("foo \"bar;baz\"", "bar \"buzz\"")
    "; foo \"bar;baz\"; bar \"buzz;two\"".parse shouldBe Seq("foo \"bar;baz\"", "bar \"buzz;two\"")
    """; foo "bar;\"baz\""; bar""".parse shouldBe Seq("""foo "bar;\"baz\""""", "bar")
    """; setStringValue "foo;bar"; checkStringValue "foo;bar"""".parse shouldBe
      Seq("""setStringValue "foo;bar"""", """checkStringValue "foo;bar"""")
  }
  it should "parse commands without leading ';'" in {
    "setStringValue foo; setStringValue bar".parse shouldBe Seq(
      "setStringValue foo",
      "setStringValue bar"
    )
    "foo; bar".parse shouldBe Seq("foo", "bar")
    "foo bar ;bar".parse shouldBe Seq("foo bar", "bar")
    "foo \"a;b\"; bar".parse shouldBe Seq("foo \"a;b\"", "bar")
    " foo ; bar \"b;c\"".parse shouldBe Seq("foo", "bar \"b;c\"")
  }
  it should "not parse single commands without leading ';'" in {
    "foo".parseEither shouldBe Left("Expected ';'\nfoo\n   ^")
    "foo bar baz".parseEither shouldBe Left("Expected ';'\nfoo bar baz\n           ^")
  }
  it should "not parse empty commands" in {
    assert(";;;".parseEither.isLeft)
    assert("; ; ;".parseEither.isLeft)
  }
  it should "parse commands with trailing semi-colon" in {
    assert("foo;bar;".parse == Seq("foo", "bar"))
    assert("foo;   bar    ;".parse == Seq("foo", "bar"))
  }
  val consecutive = "{ { val x = 1}; { val x = 2 } }"
  val oneBrace = "set foo := { val x = 1; x + 1 }"
  val twoBrace = "set foo := { val x = { val y = 2; y + 2 }; x + 1 }"
  val threeBrace = "set foo := { val x = { val y = 2; { val z = 3; y + 2 } }; x + 1 }"
  val doubleBrace = "set foo := { val x = { val y = 2; y + 2 }; { x + 1 } }"
  val tripleBrace = "set foo := { val x = { val y = 2; y + 2 }; val y = { x + 1 }; { z + y } }"
  val emptyBraces = "{{{{}}}}"
  it should "parse commands with braces" in {
    assert(s"$consecutive;".parse == consecutive :: Nil)
    assert(s"$oneBrace;".parse == oneBrace :: Nil)
    assert(s"$twoBrace;".parse == twoBrace :: Nil)
    assert(s"$threeBrace;".parse == threeBrace :: Nil)
    assert(s"$doubleBrace;".parse == doubleBrace :: Nil)
    assert(s"$tripleBrace;".parse == tripleBrace :: Nil)
    assert(s"$emptyBraces;".parse == emptyBraces :: Nil)
  }
  it should "parse multiple commands with braces" in {
    s"compile; $consecutive".parse shouldBe "compile" :: consecutive :: Nil
    s"compile; $consecutive ; test".parse shouldBe "compile" :: consecutive :: "test" :: Nil
  }
  it should "not parse unclosed braces" in {
    val extraRight = "{ { val x = 1}}{ val x = 2 } }"
    assert(s"compile; $extraRight".parseEither.isLeft)
    val extraLeft = "{{{ val x = 1}{ val x = 2 } }"
    assert(s"compile; $extraLeft".parseEither.isLeft)
    val unmatchedEmptyBraces = "{{{{}}}"
    assert(s"compile; $unmatchedEmptyBraces".parseEither.isLeft)
  }
}
