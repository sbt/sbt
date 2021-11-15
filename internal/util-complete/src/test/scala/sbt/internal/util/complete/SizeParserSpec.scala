/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util.complete

import org.scalatest.flatspec.AnyFlatSpec

class SizeParserSpec extends AnyFlatSpec {
  "SizeParser" should "handle raw bytes" in {
    assert(Parser.parse(str = "123456", SizeParser.value) == Right(123456L))
  }
  it should "handle bytes" in {
    assert(Parser.parse(str = "123456b", SizeParser.value) == Right(123456L))
    assert(Parser.parse(str = "123456B", SizeParser.value) == Right(123456L))
    assert(Parser.parse(str = "123456 b", SizeParser.value) == Right(123456L))
    assert(Parser.parse(str = "123456 B", SizeParser.value) == Right(123456L))
  }
  it should "handle kilobytes" in {
    assert(Parser.parse(str = "123456k", SizeParser.value) == Right(123456L * 1024))
    assert(Parser.parse(str = "123456K", SizeParser.value) == Right(123456L * 1024))
    assert(Parser.parse(str = "123456 K", SizeParser.value) == Right(123456L * 1024))
    assert(Parser.parse(str = "123456 K", SizeParser.value) == Right(123456L * 1024))
  }
  it should "handle megabytes" in {
    assert(Parser.parse(str = "123456m", SizeParser.value) == Right(123456L * 1024 * 1024))
    assert(Parser.parse(str = "123456M", SizeParser.value) == Right(123456L * 1024 * 1024))
    assert(Parser.parse(str = "123456 M", SizeParser.value) == Right(123456L * 1024 * 1024))
    assert(Parser.parse(str = "123456 M", SizeParser.value) == Right(123456L * 1024 * 1024))
  }
  it should "handle gigabytes" in {
    assert(Parser.parse(str = "123456g", SizeParser.value) == Right(123456L * 1024 * 1024 * 1024))
    assert(Parser.parse(str = "123456G", SizeParser.value) == Right(123456L * 1024 * 1024 * 1024))
    assert(Parser.parse(str = "123456 G", SizeParser.value) == Right(123456L * 1024 * 1024 * 1024))
    assert(Parser.parse(str = "123456 G", SizeParser.value) == Right(123456L * 1024 * 1024 * 1024))
  }
  it should "handle doubles" in {
    assert(Parser.parse(str = "1.25g", SizeParser.value) == Right(5L * 1024 * 1024 * 1024 / 4))
    assert(Parser.parse(str = "1.25 g", SizeParser.value) == Right(5L * 1024 * 1024 * 1024 / 4))
    assert(Parser.parse(str = "1.25 g", SizeParser.value) == Right(5L * 1024 * 1024 * 1024 / 4))
    assert(Parser.parse(str = "1.25 G", SizeParser.value) == Right(5L * 1024 * 1024 * 1024 / 4))
  }
  private val expectedCompletions = Set("", "b", "B", "g", "G", "k", "K", "m", "M", " ")
  it should "have completions for long" in {
    val completions = Parser.completions(SizeParser.value, "123", level = 0).get.map(_.display)
    assert(completions == expectedCompletions)
  }
  it should "have completions for long with spaces" in {
    val completions = Parser.completions(SizeParser.value, "123", level = 0).get.map(_.display)
    assert(completions == expectedCompletions)
  }
  it should "have completions for double " in {
    val completions = Parser.completions(SizeParser.value, "1.25", level = 0).get.map(_.display)
    assert(completions == expectedCompletions)
  }
  it should "have completions for double with spaces" in {
    val completions = Parser.completions(SizeParser.value, "1.25  ", level = 0).get.map(_.display)
    assert(completions == expectedCompletions + "")
  }
}
