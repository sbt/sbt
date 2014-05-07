/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah */

package sbt

import org.scalacheck._
import Prop._

object NameFilterSpecification extends Properties("NameFilter") {
  property("All pass accepts everything") = forAll { (s: String) => AllPassFilter.accept(s) }
  property("Exact filter matches provided string") = forAll {
    (s1: String, s2: String) => (new ExactFilter(s1)).accept(s2) == (s1 == s2)
  }
  property("Exact filter matches valid string") = forAll { (s: String) => (new ExactFilter(s)).accept(s) }

  property("Glob filter matches provided string if no *s") = forAll {
    (s1: String, s2: String) =>
      {
        val stripped = stripAsterisksAndControl(s1)
        (GlobFilter(stripped).accept(s2) == (stripped == s2))
      }
  }
  property("Glob filter matches valid string if no *s") = forAll {
    (s: String) =>
      {
        val stripped = stripAsterisksAndControl(s)
        GlobFilter(stripped).accept(stripped)
      }
  }

  property("Glob filter matches valid") = forAll {
    (list: List[String]) =>
      {
        val stripped = list.map(stripAsterisksAndControl)
        GlobFilter(stripped.mkString("*")).accept(stripped.mkString)
      }
  }

  /**
   * Raw control characters are stripped because they are not allowed in expressions.
   * Asterisks are stripped because they are added under the control of the tests.
   */
  private def stripAsterisksAndControl(s: String) = (s filter validChar).toString
  private[this] def validChar(c: Char) =
    !java.lang.Character.isISOControl(c) &&
      c != '*' &&
      !Character.isHighSurrogate(c) &&
      !Character.isLowSurrogate(c)
}