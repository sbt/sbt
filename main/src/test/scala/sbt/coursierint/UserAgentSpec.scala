/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.coursierint

import hedgehog.*
import hedgehog.runner.*

object UserAgentSpec extends Properties:
  private val UserAgent =
    raw"""Coursier/\d+\.\d+ \(\+https://github\.com/coursier\) sbt/2\.0 \(\+https://www\.scala-sbt\.org/\)""".r

  override def tests: List[Test] =
    List(
      property(
        "defaultUserAgent only includes major.minor of sbt",
        versionGen.forAll.map { (major, minor, rest) =>
          val ua = LMCoursier.defaultUserAgent(s"$major.$minor$rest")
          Result.assert(ua.endsWith(s" sbt/$major.$minor (+https://www.scala-sbt.org/)"))
        }
      ),
      example(
        "defaultUserAgent only includes major.minor of Coursier",
        Result.assert(UserAgent.matches(LMCoursier.defaultUserAgent("2.0.0")))
      ),
    )

  private def versionGen: Gen[(Int, Int, String)] =
    for
      major <- Gen.int(Range.linear(0, 100))
      minor <- Gen.int(Range.linear(0, 100))
      patch <- Gen.int(Range.linear(0, 100))
      suffix <- Gen.element1("", "-M2", "-RC1", "-SNAPSHOT", "-bin-20260924")
      rest <- Gen.element1("", s".$patch$suffix")
    yield (major, minor, rest)
end UserAgentSpec
