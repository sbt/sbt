package sbt

import org.specs2._

class VersionRangeSpec extends Specification {
  def is = s2"""

  This is a specification to check the version range parsing.

  1.0 should
    ${stripTo("1.0", None)}
  (,1.0] should
    ${stripTo("(,1.0]", Some("1.0"))}
  (,1.0) should
    ${stripTo("(,1.0)", None)}
  [1.0] should
    ${stripTo("[1.0]", Some("1.0"))}
  [1.0,) should
    ${stripTo("[1.0,)", Some("1.0"))}
  (1.0,) should
    ${stripTo("(1.0,)", Some("1.0"))}
  (1.0,2.0) should
    ${stripTo("(1.0,2.0)", None)}
  [1.0,2.0] should
    ${stripTo("[1.0,2.0]", None)}
  (,1.0],[1.2,) should
    ${stripTo("(,1.0],[1.2,)", Some("1.0"))}
  (,1.1),(1.1,) should
    ${stripTo("(,1.1),(1.1,)", None)}
  """

  def stripTo(s: String, expected: Option[String]) =
    VersionRange.stripMavenVersionRange(s) must_== expected
}
