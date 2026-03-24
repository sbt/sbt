/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import hedgehog._
import hedgehog.runner._

object VcsUriFragmentTest extends Properties {
  override def tests: List[Test] = List(
    example("accepts typical branch and tag names", testAcceptsSafe),
    example("accepts hex commit id fragment", testAcceptsHexSha),
    example("rejects empty fragment", testRejectsEmpty),
    example("rejects ampersand", testRejectsAmpersand),
    example("rejects pipe", testRejectsPipe),
    example("rejects semicolon", testRejectsSemicolon),
    example("rejects space", testRejectsSpace),
    example("rejects percent", testRejectsPercent),
    example("rejects greater-than", testRejectsGreaterThan),
    example("rejects newline", testRejectsNewline),
    example("rejects DEL", testRejectsDel),
  )

  def testAcceptsSafe: Result = {
    VcsUriFragment.validate("develop")
    VcsUriFragment.validate("v1.2.3")
    VcsUriFragment.validate("feature/foo-bar")
    VcsUriFragment.validate("release/1.0.0+build")
    Result.success
  }

  def testAcceptsHexSha: Result = {
    VcsUriFragment.validate("abc123def4567890abcdef1234567890abcdef12")
    Result.success
  }

  def testRejectsEmpty: Result =
    interceptIllegal("")

  def testRejectsAmpersand: Result =
    interceptIllegal("a&b")

  def testRejectsPipe: Result =
    interceptIllegal("a|b")

  def testRejectsSemicolon: Result =
    interceptIllegal("a;b")

  def testRejectsSpace: Result =
    interceptIllegal("a b")

  def testRejectsPercent: Result =
    interceptIllegal("a%20b")

  def testRejectsGreaterThan: Result =
    interceptIllegal("a>b")

  def testRejectsNewline: Result =
    interceptIllegal("a\nb")

  def testRejectsDel: Result =
    interceptIllegal("a\u007fb")

  private def interceptIllegal(s: String): Result =
    try {
      VcsUriFragment.validate(s)
      Result.failure.log(s"expected failure for ${s.map(_.toInt).mkString(",")}")
    } catch {
      case _: IllegalArgumentException => Result.success
    }

}
