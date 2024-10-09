package sbt
package internal
package librarymanagement

class VersionRangeSpec extends UnitSpec {
  "Version range" should "strip 1.0 to None" in stripTo("1.0", None)
  it should "strip (,1.0] to 1.0" in stripTo("(,1.0]", Some("1.0"))
  it should "strip (,1.0) to None" in stripTo("(,1.0)", None)
  it should "strip [1.0] to 1.0" in stripTo("[1.0]", Some("1.0"))
  it should "strip [1.0,) to 1.0" in stripTo("[1.0,)", Some("1.0"))
  it should "strip (1.0,) to 1.0" in stripTo("(1.0,)", Some("1.0"))
  it should "strip (1.0,2.0) to None" in stripTo("(1.0,2.0)", None)
  it should "strip [1.0,2.0] to None" in stripTo("[1.0,2.0]", None)
  it should "strip (,1.0],[1.2,) to 1.0" in stripTo("(,1.0],[1.2,)", Some("1.0"))
  it should "strip (,1.1),(1.1,) to None" in stripTo("(,1.1),(1.1,)", None)

  def stripTo(s: String, expected: Option[String]) =
    assert(VersionRange.stripMavenVersionRange(s) == expected)
}
