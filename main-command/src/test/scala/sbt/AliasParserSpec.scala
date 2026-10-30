/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.AttributeMap
import sbt.internal.util.complete.Parser
import sbt.internal.util.complete.DefaultParsers.*
import verify.BasicTestSuite

object AliasParserSpec extends BasicTestSuite:
  val aliases: Seq[(String, String)] = Seq(
    "mima" -> "a/mimaReportBinaryIssues;b/mimaReportBinaryIssues",
    "quick" -> "scalafmtAll;jvm/Test/compile;jvm/testQuick",
    "cleanpile" -> "clean;jvm/Test/compile",
    "precommit" -> "clean;docs/clean;scalafmtAll;jvm/Test/compile;jvm/test",
    "ci-jvm-3" -> "clean;docs/clean;jvm/test;mima;jvm/doc",
    "ci-jvm" -> "ci-jvm-3",
    "ci-js" -> "clean;js/Test/compile;js/test",
    "ci-native" -> "clean;native/test",
    "ci" -> "ci-jvm;ci-js;ci-native",
    "it" -> "clean;it/clean;it/test",
    "a" -> "compile",
    "b" -> "quick",
    "c" -> "precommit",
    "d" -> "ci",
    "e" -> "benchmark",
    "f" -> "mima",
  )

  final class Fixture(as: Seq[(String, String)]):
    var parserBuilds = 0

    val keyParser: Parser[String] =
      (ID ~ ('/' ~> charClass(c => c != ' ' && c != ';').+.string).? ~ (Space ~> ID).*).map {
        case a ~ b ~ args => ((a + b.fold("")("/" + _)) +: args).mkString(" ")
      }

    val state: State =
      val key = Command.arb { _ =>
        parserBuilds += 1
        keyParser
      }((s, k) => s"ran $k" :: s)
      val base = State(
        null,
        BasicCommands.allBasicCommands ++ Seq(BasicCommands.multi, key),
        Set.empty,
        None,
        Nil,
        State.newHistory,
        AttributeMap.empty,
        null,
        None,
        State.Continue
      )
      as.foldLeft(base) { case (s, (n, v)) => BasicCommands.addAlias(s, n, v) }

    def run(command: String): Either[String, List[String]] =
      parserBuilds = 0
      Parser.parse(command, state.combinedParser).map(_().remainingCommands.map(_.commandLine))

    def completions(command: String): Set[String] =
      Parser.completions(state.combinedParser, command, 9).get.map(_.append)
  end Fixture

  test("alias without arguments expands to its body"):
    assert(Fixture(aliases).run("ci") == Right(List("ci-jvm;ci-js;ci-native")))

  test("alias with arguments appends them to its body"):
    assert(Fixture(Seq("t" -> "jvm/testOnly")).run("t foo") == Right(List("jvm/testOnly foo")))

  test("alias with an invalid body is not expanded"):
    val f = Fixture(Seq("x" -> "foo %"))
    assert(f.run("x") == Right(List("ran x")))
    assert(f.run("x y") == Right(List("ran x y")))

  test("single letter alias does not capture a longer command"):
    assert(Fixture(aliases).run("clean") == Right(List("ran clean")))

  test("alias name completes to a single space for its arguments"):
    val completions = Fixture(Seq("t" -> "jvm/testOnly")).completions("t")
    assert(completions.contains(" "))
    assert(!completions.contains("  "))

  test("aliases that prefix a command are not expanded while parsing it (#9945)"):
    val f = Fixture(aliases)
    assert(f.run("clean").isRight)
    assert(f.parserBuilds <= 2)
    assert(f.run("clean;docs/clean").isRight)
    assert(f.parserBuilds <= 4)

  test("nested aliases are parsed a bounded number of times (#9945)"):
    val f = Fixture(aliases)
    assert(f.run("ci").isRight)
    assert(f.parserBuilds <= 30)
end AliasParserSpec
