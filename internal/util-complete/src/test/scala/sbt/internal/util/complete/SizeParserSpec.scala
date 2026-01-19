/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util.complete

import verify.BasicTestSuite

object SizeParserSpec extends BasicTestSuite:
  test("SizeParser should handle raw bytes"):
    assert(Parser.parse(str = "123456", SizeParser.value) == Right(123456L))

  test("SizeParser should handle bytes"):
    assert(Parser.parse(str = "123456b", SizeParser.value) == Right(123456L))
    assert(Parser.parse(str = "123456B", SizeParser.value) == Right(123456L))
    assert(Parser.parse(str = "123456 b", SizeParser.value) == Right(123456L))
    assert(Parser.parse(str = "123456 B", SizeParser.value) == Right(123456L))

  test("SizeParser should handle kilobytes"):
    assert(Parser.parse(str = "123456k", SizeParser.value) == Right(123456L * 1024))
    assert(Parser.parse(str = "123456K", SizeParser.value) == Right(123456L * 1024))
    assert(Parser.parse(str = "123456 K", SizeParser.value) == Right(123456L * 1024))
    assert(Parser.parse(str = "123456 K", SizeParser.value) == Right(123456L * 1024))

  test("SizeParser should handle megabytes"):
    assert(Parser.parse(str = "123456m", SizeParser.value) == Right(123456L * 1024 * 1024))
    assert(Parser.parse(str = "123456M", SizeParser.value) == Right(123456L * 1024 * 1024))
    assert(Parser.parse(str = "123456 M", SizeParser.value) == Right(123456L * 1024 * 1024))
    assert(Parser.parse(str = "123456 M", SizeParser.value) == Right(123456L * 1024 * 1024))

  test("SizeParser should handle gigabytes"):
    assert(Parser.parse(str = "123456g", SizeParser.value) == Right(123456L * 1024 * 1024 * 1024))
    assert(Parser.parse(str = "123456G", SizeParser.value) == Right(123456L * 1024 * 1024 * 1024))
    assert(Parser.parse(str = "123456 G", SizeParser.value) == Right(123456L * 1024 * 1024 * 1024))
    assert(Parser.parse(str = "123456 G", SizeParser.value) == Right(123456L * 1024 * 1024 * 1024))

  test("SizeParser should handle doubles"):
    assert(Parser.parse(str = "1.25g", SizeParser.value) == Right(5L * 1024 * 1024 * 1024 / 4))
    assert(Parser.parse(str = "1.25 g", SizeParser.value) == Right(5L * 1024 * 1024 * 1024 / 4))
    assert(Parser.parse(str = "1.25 g", SizeParser.value) == Right(5L * 1024 * 1024 * 1024 / 4))
    assert(Parser.parse(str = "1.25 G", SizeParser.value) == Right(5L * 1024 * 1024 * 1024 / 4))

  private val expectedCompletions: Set[String] =
    Set("", "b", "B", "g", "G", "k", "K", "m", "M", " ")

  test("SizeParser should have completions for long"):
    val completions = Parser.completions(SizeParser.value, "123", level = 0).get.map(_.display)
    assert(completions == expectedCompletions)

  test("SizeParser should have completions for long with spaces"):
    val completions = Parser.completions(SizeParser.value, "123", level = 0).get.map(_.display)
    assert(completions == expectedCompletions)

  test("SizeParser should have completions for double"):
    val completions = Parser.completions(SizeParser.value, "1.25", level = 0).get.map(_.display)
    assert(completions == expectedCompletions)

  test("SizeParser should have completions for double with spaces"):
    val completions = Parser.completions(SizeParser.value, "1.25  ", level = 0).get.map(_.display)
    assert(completions == expectedCompletions + "")
end SizeParserSpec
