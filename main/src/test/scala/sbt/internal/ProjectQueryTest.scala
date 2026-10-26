/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import hedgehog.*
import hedgehog.runner.*
import _root_.sbt.internal.util.complete.Parser

object ProjectQueryTest extends Properties:
  override def tests: List[Test] = List(
    example("parses wildcard", testWildcard),
    example("parses scalaBinaryVersion", testScalaBinaryVersion),
    example("parses platform", testPlatform),
    example("parses multiple params", testMultiple),
    property("parses any platform value", propPlatform),
    example("rejects unknown param", testUnknown),
    example("rejects plain project name", testPlain),
  )

  def parse(s: String): Option[ProjectQuery] =
    Parser.parse(s, ProjectQuery.parser).toOption

  def testWildcard: Result =
    parse("...") ==== Some(ProjectQuery("...", Map.empty))

  def testScalaBinaryVersion: Result =
    parse("...@scalaBinaryVersion=2.13") ====
      Some(ProjectQuery("...", Map(Keys.scalaBinaryVersion.key -> "2.13")))

  def testPlatform: Result =
    parse("foo...@platform=sjs1") ====
      Some(ProjectQuery("foo...", Map(Keys.platform.key -> "sjs1")))

  def testMultiple: Result =
    parse("...@scalaBinaryVersion=3@platform=native0.5") ====
      Some(
        ProjectQuery(
          "...",
          Map(Keys.scalaBinaryVersion.key -> "3", Keys.platform.key -> "native0.5"),
        )
      )

  def propPlatform: Property =
    for value <- Gen
        .string(Gen.choice1(Gen.alphaNum, Gen.element1('.', '_', '-')), Range.linear(1, 10))
        .forAll
    yield parse(s"foo@platform=$value") ====
      Some(ProjectQuery("foo", Map(Keys.platform.key -> value)))

  def testUnknown: Result =
    parse("...@organization=foo") ==== None

  def testPlain: Result =
    parse("foo") ==== None
end ProjectQueryTest
