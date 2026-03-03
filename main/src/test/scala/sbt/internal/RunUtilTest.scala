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

object RunUtilTest extends Properties:
  override def tests: List[Test] = List(
    property("mkWindowTitle formats title correctly", testMkWindowTitle),
    property("mkWindowTitle handles empty strings", testMkWindowTitleEmpty),
    property("splitArgs with no -- treats all as app args", testSplitArgsNoDash),
    property("splitArgs splits at first --", testSplitArgsWithDash),
    property("splitArgs with only -- gives empty jvm and app args", testSplitArgsOnlyDash),
    property("splitArgs with -- at start gives empty jvm args", testSplitArgsDashAtStart),
    property("splitArgs with -- at end gives empty app args", testSplitArgsDashAtEnd),
    property("splitArgs with multiple -- splits at first only", testSplitArgsMultipleDash),
    property("splitArgs with empty input", testSplitArgsEmpty),
  )

  def testMkWindowTitle: Property =
    for
      command <- Gen.element1("run", "runMain", "bgRun", "bgRunMain").forAll
      org <- Gen.string(Gen.alpha, Range.linear(1, 20)).forAll
      name <- Gen.string(Gen.alpha, Range.linear(1, 20)).forAll
      version <- Gen.string(Gen.alphaNum, Range.linear(1, 10)).forAll
    yield
      val result = RunUtil.mkWindowTitle(command, org, name, version)
      val expected = s"sbt $command: $org % $name % $version"
      Result.assert(result == expected)

  def testMkWindowTitleEmpty: Property =
    for _ <- Gen.constant(()).forAll
    yield
      val result = RunUtil.mkWindowTitle("run", "", "", "")
      Result.assert(result == "sbt run:  %  % ")

  def testSplitArgsNoDash: Property =
    for _ <- Gen.constant(()).forAll
    yield
      val (jvm, app) = RunUtil.splitArgs(Seq("arg1", "arg2"))
      Result.assert(jvm.isEmpty).and(Result.assert(app == Seq("arg1", "arg2")))

  def testSplitArgsWithDash: Property =
    for _ <- Gen.constant(()).forAll
    yield
      val (jvm, app) = RunUtil.splitArgs(Seq("-Xmx2G", "-ea", "--", "arg1", "arg2"))
      Result.assert(jvm == Seq("-Xmx2G", "-ea")).and(Result.assert(app == Seq("arg1", "arg2")))

  def testSplitArgsOnlyDash: Property =
    for _ <- Gen.constant(()).forAll
    yield
      val (jvm, app) = RunUtil.splitArgs(Seq("--"))
      Result.assert(jvm.isEmpty).and(Result.assert(app.isEmpty))

  def testSplitArgsDashAtStart: Property =
    for _ <- Gen.constant(()).forAll
    yield
      val (jvm, app) = RunUtil.splitArgs(Seq("--", "arg1"))
      Result.assert(jvm.isEmpty).and(Result.assert(app == Seq("arg1")))

  def testSplitArgsDashAtEnd: Property =
    for _ <- Gen.constant(()).forAll
    yield
      val (jvm, app) = RunUtil.splitArgs(Seq("-Xmx2G", "--"))
      Result.assert(jvm == Seq("-Xmx2G")).and(Result.assert(app.isEmpty))

  def testSplitArgsMultipleDash: Property =
    for _ <- Gen.constant(()).forAll
    yield
      val (jvm, app) = RunUtil.splitArgs(Seq("-Xmx2G", "--", "arg1", "--", "arg2"))
      Result
        .assert(jvm == Seq("-Xmx2G"))
        .and(Result.assert(app == Seq("arg1", "--", "arg2")))

  def testSplitArgsEmpty: Property =
    for _ <- Gen.constant(()).forAll
    yield
      val (jvm, app) = RunUtil.splitArgs(Seq.empty)
      Result.assert(jvm.isEmpty).and(Result.assert(app.isEmpty))
end RunUtilTest
