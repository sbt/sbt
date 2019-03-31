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
    "foo bar baz;".parseEither shouldBe
      Left("Expected not ';'\nExpected '\"'\nfoo bar baz;\n            ^")
    "foo;".parseEither shouldBe Left("Expected not ';'\nExpected '\"'\nfoo;\n    ^")
  }
  it should "parse commands with trailing semi-colon" in {
    "foo;bar;".parse shouldBe Seq("foo", "bar")
    "foo;   bar    ;".parse shouldBe Seq("foo", "bar")
  }
}
