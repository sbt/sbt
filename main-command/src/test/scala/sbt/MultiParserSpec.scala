/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import scala.concurrent.duration.*
import sbt.internal.util.complete.Parser
import verify.BasicTestSuite

object MultiParserSpec extends BasicTestSuite:
  val parser: Parser[Seq[String]] = BasicCommands.multiParserImpl(None)

  extension (s: String)
    def parse: Seq[String] = Parser.parse(s, parser) match
      case Right(x) => x
      case Left(x)  => sys.error(s)
    def parseEither: Either[String, Seq[String]] = Parser.parse(s, parser)

  test("parsing should parse single commands"):
    assert(";foo".parse == Seq("foo"))
    assert(";   foo".parse == Seq("foo"))

  test("parsing should parse multiple commands"):
    assert(";foo;bar".parse == Seq("foo", "bar"))

  test("parsing should parse single command with leading spaces"):
    assert(";     foo".parse == Seq("foo"))
    assert("     ;     foo".parse == Seq("foo"))
    assert("      foo;".parse == Seq("foo"))

  test("parsing should parse single command with trailing spaces"):
    assert(";     foo      ".parse == Seq("foo"))
    assert(";foo      ".parse == Seq("foo"))
    assert("foo;      ".parse == Seq("foo"))
    assert("    foo;    ".parse == Seq("foo"))
    assert("      foo   ;    ".parse == Seq("foo"))

  test("parsing should parse multiple commands with leading spaces"):
    assert(";     foo;bar".parse == Seq("foo", "bar"))
    assert(";     foo;    bar".parse == Seq("foo", "bar"))
    assert(";foo; bar".parse == Seq("foo", "bar"))
    assert("; foo ; bar ; baz".parse == Seq("foo", "bar", "baz"))

  test("parsing should parse command with string literal"):
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

  test("parsing should parse commands without leading ';'"):
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

  test("parsing should not parse single commands without leading ';'"):
    assert("foo".parseEither == Left("Expected ';'\nfoo\n   ^"))
    assert("foo bar baz".parseEither == Left("Expected ';'\nfoo bar baz\n           ^"))

  test("parsing should not parse empty commands"):
    assert(";;;".parseEither.isLeft)
    assert("; ; ;".parseEither.isLeft)

  test("parsing should parse commands with trailing semi-colon"):
    assert("foo;bar;".parse == Seq("foo", "bar"))
    assert("foo;   bar    ;".parse == Seq("foo", "bar"))

  val consecutive: String = "{ { val x = 1}; { val x = 2 } }"
  val oneBrace: String = "set foo := { val x = 1; x + 1 }"
  val twoBrace: String = "set foo := { val x = { val y = 2; y + 2 }; x + 1 }"
  val threeBrace: String = "set foo := { val x = { val y = 2; { val z = 3; y + 2 } }; x + 1 }"
  val doubleBrace: String = "set foo := { val x = { val y = 2; y + 2 }; { x + 1 } }"
  val tripleBrace: String =
    "set foo := { val x = { val y = 2; y + 2 }; val y = { x + 1 }; { z + y } }"
  val emptyBraces: String = "{{{{}}}}"

  test("parsing should parse commands with braces"):
    Predef.assert(s"$consecutive;".parse == consecutive :: Nil)
    Predef.assert(s"$oneBrace;".parse == oneBrace :: Nil)
    Predef.assert(s"$twoBrace;".parse == twoBrace :: Nil)
    Predef.assert(s"$threeBrace;".parse == threeBrace :: Nil)
    Predef.assert(s"$doubleBrace;".parse == doubleBrace :: Nil)
    Predef.assert(s"$tripleBrace;".parse == tripleBrace :: Nil)
    Predef.assert(s"$emptyBraces;".parse == emptyBraces :: Nil)

  test("parsing should parse multiple commands with braces"):
    Predef.assert(s"compile; $consecutive".parse == "compile" :: consecutive :: Nil)
    Predef.assert(
      s"compile; $consecutive ; test".parse == "compile" :: consecutive :: "test" :: Nil
    )

  test("parsing should not parse unclosed braces"):
    val extraRight = "{ { val x = 1}}{ val x = 2 } }"
    Predef.assert(s"compile; $extraRight".parseEither.isLeft)
    val extraLeft = "{{{ val x = 1}{ val x = 2 } }"
    Predef.assert(s"compile; $extraLeft".parseEither.isLeft)
    val unmatchedEmptyBraces = "{{{{}}}"
    Predef.assert(s"compile; $unmatchedEmptyBraces".parseEither.isLeft)

  test("parsing should handle cosmetic whitespace"):
    val commands = (1 to 100).map(_ => "compile")
    val multiLine = commands.mkString("      \n      ;", "       \n       ;", "    \n        ")
    val start = System.nanoTime
    assert(multiLine.parse == commands)
    val elapsed = System.nanoTime - start
    // Make sure this took less than 10 seconds. It takes about 30 milliseconds to run with
    // 100 commands and 3 milliseconds with 3 commands. With a bad parser, it will run indefinitely.
    assert(elapsed.nanoseconds < 10.seconds)

  test("parsing should exclude alias"):
    val alias = """alias scalacFoo = ; set scalacOptions ++= Seq("-foo")"""
    Predef.assert(alias.parseEither.isLeft)
    Predef.assert(s"   $alias".parseEither.isLeft)
    Predef.assert(s"   $alias;".parseEither.isLeft)
    Predef.assert(s";$alias".parseEither.isLeft)
    Predef.assert(s";   $alias".parseEither.isLeft)
    Predef.assert(s";$alias;".parseEither.isLeft)
    Predef.assert(s";   $alias;".parseEither.isLeft)
    Predef.assert(s"foo; $alias".parseEither.isLeft)
    Predef.assert(s"; foo;$alias".parseEither.isLeft)
    Predef.assert(s"; foo;$alias; ".parseEither.isLeft)
    Predef.assert(s"; foo;   $alias; ".parseEither.isLeft)
end MultiParserSpec
