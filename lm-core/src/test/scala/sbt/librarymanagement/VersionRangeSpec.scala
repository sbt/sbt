package sbt
package internal
package librarymanagement

import hedgehog.*
import hedgehog.runner.*

object VersionRangeSpec extends Properties:
  override def tests: List[Test] = stripTests ++ satisfiesTests ++ isVersionRangeTests

  def stripTests: List[Test] = List(
    "1.0" -> None,
    "(,1.0]" -> Some("1.0"),
    "(,1.0)" -> None,
    "[1.0]" -> Some("1.0"),
    "[1.0,)" -> Some("1.0"),
    "(1.0,)" -> Some("1.0"),
    "(1.0,2.0)" -> None,
    "[1.0,2.0]" -> None,
    "(,1.0],[1.2,)" -> Some("1.0"),
    "(,1.1),(1.1,)" -> None,
  ).map: (s, expected) =>
    example(
      s"Version range should strip $s to ${expected.getOrElse("None")}",
      VersionRange.stripMavenVersionRange(s) ==== expected
    )

  def satisfiesTests: List[Test] = List(
    ("4.2.1", "[4.1.0,5)", true),
    ("4.1.0", "[4.1.0,5)", true),
    ("5", "[4.1.0,5)", false),
    ("5.0", "[4.1.0,5)", false),
    ("5.0.0", "[4.1.0,5)", false),
    ("4.0.9", "[4.1.0,5)", false),
    ("3.0.0", "[4.1.0,5)", false),
    ("5.0.1", "[4.1.0,5)", false),
    ("6.0.0", "[4.1.0,5)", false),
    ("1.0", "[1.0,2.0]", true),
    ("1.5", "[1.0,2.0]", true),
    ("2.0", "[1.0,2.0]", true),
    ("2.0.1", "[1.0,2.0]", false),
    ("0.9", "[1.0,2.0]", false),
    ("1.0", "(1.0,2.0)", false),
    ("1.0.1", "(1.0,2.0)", true),
    ("1.5", "(1.0,2.0)", true),
    ("1.9.9", "(1.0,2.0)", true),
    ("2.0", "(1.0,2.0)", false),
    ("1.0", "[1.0,)", true),
    ("1.5", "[1.0,)", true),
    ("100.0", "[1.0,)", true),
    ("0.9", "[1.0,)", false),
    ("1.4.7", "[1.3.0,)", true),
    ("1.3.0", "[1.3.0,)", true),
    ("1.2.9", "[1.3.0,)", false),
    ("0.1", "(,2.0]", true),
    ("1.0", "(,2.0]", true),
    ("2.0", "(,2.0]", true),
    ("2.0.1", "(,2.0]", false),
    ("1.0", "1.0+", true),
    ("1.1", "1.0+", true),
    ("2.0", "1.0+", true),
    ("0.9", "1.0+", false),
    ("1.0", "1.0", true),
    ("1.0.0", "1.0", false),
    ("1.1", "1.0", false),
    ("1.0", "[1.0]", true),
    ("1.0.0", "[1.0]", true),
    ("1.1", "[1.0]", false),
    ("2.3", "[1.3.1,2.3]", true),
    ("1.3.1", "[1.3.1,2.3]", true),
    ("2.4", "[1.3.1,2.3]", false),
    ("2.3", "1.3.1,2.3", true),
    ("1.3.1", "1.3.1,2.3", true),
    ("2.4", "1.3.1,2.3", false),
  ).map: (version, range, expected) =>
    example(
      s"versionSatisfiesRange($version, $range) should return $expected",
      VersionRange.versionSatisfiesRange(version, range) ==== expected
    )

  def isVersionRangeTests: List[Test] = List(
    example(
      "isVersionRange should accept [1.3.1,2.3] (#6244)",
      Result.assert(VersionRange.isVersionRange("[1.3.1,2.3]"))
    ),
    example(
      "isVersionRange should accept comma-separated range without brackets (#6244)",
      Result.assert(VersionRange.isVersionRange("1.3.1,2.3"))
    ),
  )
end VersionRangeSpec
