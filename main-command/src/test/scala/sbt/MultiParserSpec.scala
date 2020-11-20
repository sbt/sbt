/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import scala.concurrent.duration._
import org.scalatest.FlatSpec
import sbt.internal.util.complete.Parser

object MultiParserSpec {
  val parser: Parser[Seq[String]] = BasicCommands.multiParserImpl(None)
  implicit class StringOps(val s: String) {
    def parse: Seq[String] = Parser.parse(s, parser) match {
      case Right(x) => x
      case Left(x)  => sys.error(s)
    }
    def parseEither: Either[String, Seq[String]] = Parser.parse(s, parser)
  }
}
import sbt.MultiParserSpec._
class MultiParserSpec extends FlatSpec {
  "parsing" should "parse single commands" in {
    assert(";foo".parse == Seq("foo"))
    assert(";   foo".parse == Seq("foo"))
  }
  it should "parse multiple commands" in {
    assert(";foo;bar".parse == Seq("foo", "bar"))
  }
  it should "parse single command with leading spaces" in {
    assert(";     foo".parse == Seq("foo"))
    assert("     ;     foo".parse == Seq("foo"))
    assert("      foo;".parse == Seq("foo"))
  }
  it should "parse single command with trailing spaces" in {
    assert(";     foo      ".parse == Seq("foo"))
    assert(";foo      ".parse == Seq("foo"))
    assert("foo;      ".parse == Seq("foo"))
    assert("    foo;    ".parse == Seq("foo"))
    assert("      foo   ;    ".parse == Seq("foo"))
  }
  it should "parse multiple commands with leading spaces" in {
    assert(";     foo;bar".parse == Seq("foo", "bar"))
    assert(";     foo;    bar".parse == Seq("foo", "bar"))
    assert(";foo; bar".parse == Seq("foo", "bar"))
    assert("; foo ; bar ; baz".parse == Seq("foo", "bar", "baz"))
  }
  it should "parse command with string literal" in {
    assert("; foo \"barbaz\"".parse == Seq("foo \"barbaz\""))
    assert("; foo \"bar;baz\"".parse == Seq("foo \"bar;baz\""))
    assert("; foo \"barbaz\"; bar".parse == Seq("foo \"barbaz\"", "bar"))
    assert("; foo \"barbaz\"; bar \"blah\"".parse == Seq("foo \"barbaz\"", "bar \"blah\""))
    assert("; foo \"bar;baz\"; bar".parse == Seq("foo \"bar;baz\"", "bar"))
    assert("; foo \"bar;baz\"; bar \"buzz\"".parse == Seq("foo \"bar;baz\"", "bar \"buzz\""))
    assert(
      "; foo \"bar;baz\"; bar \"buzz;two\"".parse == Seq("foo \"bar;baz\"", "bar \"buzz;two\"")
    )
    assert("""; foo "bar;\"baz\""; bar""".parse == Seq("""foo "bar;\"baz\""""", "bar"))
    assert(
      """; setStringValue "foo;bar"; checkStringValue "foo;bar"""".parse ==
        Seq("""setStringValue "foo;bar"""", """checkStringValue "foo;bar"""")
    )
  }
  it should "parse commands without leading ';'" in {
    assert(
      "setStringValue foo; setStringValue bar".parse == Seq(
        "setStringValue foo",
        "setStringValue bar"
      )
    )
    assert("foo; bar".parse == Seq("foo", "bar"))
    assert("foo bar ;bar".parse == Seq("foo bar", "bar"))
    assert("foo \"a;b\"; bar".parse == Seq("foo \"a;b\"", "bar"))
    assert(" foo ; bar \"b;c\"".parse == Seq("foo", "bar \"b;c\""))
  }
  it should "not parse single commands without leading ';'" in {
    assert("foo".parseEither == Left("Expected ';'\nfoo\n   ^"))
    assert("foo bar baz".parseEither == Left("Expected ';'\nfoo bar baz\n           ^"))
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
    assert(s"compile; $consecutive".parse == "compile" :: consecutive :: Nil)
    assert(s"compile; $consecutive ; test".parse == "compile" :: consecutive :: "test" :: Nil)
  }
  it should "not parse unclosed braces" in {
    val extraRight = "{ { val x = 1}}{ val x = 2 } }"
    assert(s"compile; $extraRight".parseEither.isLeft)
    val extraLeft = "{{{ val x = 1}{ val x = 2 } }"
    assert(s"compile; $extraLeft".parseEither.isLeft)
    val unmatchedEmptyBraces = "{{{{}}}"
    assert(s"compile; $unmatchedEmptyBraces".parseEither.isLeft)
  }
  it should "handle cosmetic whitespace" in {
    val commands = (1 to 100).map(_ => "compile")
    val multiLine = commands.mkString("      \n      ;", "       \n       ;", "    \n        ")
    val start = System.nanoTime
    assert(multiLine.parse == commands)
    val elapsed = System.nanoTime - start
    // Make sure this took less than 10 seconds. It takes about 30 milliseconds to run with
    // 100 commands and 3 milliseconds with 3 commands. With a bad parser, it will run indefinitely.
    assert(elapsed.nanoseconds < 10.seconds)
  }
  it should "exclude alias" in {
    val alias = """alias scalacFoo = ; set scalacOptions ++= Seq("-foo")"""
    assert(alias.parseEither.isLeft)
    assert(s"   $alias".parseEither.isLeft)
    assert(s"   $alias;".parseEither.isLeft)
    assert(s";$alias".parseEither.isLeft)
    assert(s";   $alias".parseEither.isLeft)
    assert(s";$alias;".parseEither.isLeft)
    assert(s";   $alias;".parseEither.isLeft)
    assert(s"foo; $alias".parseEither.isLeft)
    assert(s"; foo;$alias".parseEither.isLeft)
    assert(s"; foo;$alias; ".parseEither.isLeft)
    assert(s"; foo;   $alias; ".parseEither.isLeft)
  }
}
