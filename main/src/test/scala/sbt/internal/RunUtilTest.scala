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
end RunUtilTest
