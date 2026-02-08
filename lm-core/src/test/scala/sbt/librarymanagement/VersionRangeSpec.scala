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

  // Tests for versionSatisfiesRange (issue #3978)
  "versionSatisfiesRange" should "return true when version is within inclusive range [4.1.0,5)" in {
    assert(VersionRange.versionSatisfiesRange("4.2.1", "[4.1.0,5)") == true)
  }

  it should "return true for version at lower bound of inclusive range" in {
    assert(VersionRange.versionSatisfiesRange("4.1.0", "[4.1.0,5)") == true)
  }

  it should "return false for version at upper bound of exclusive range" in {
    assert(VersionRange.versionSatisfiesRange("5", "[4.1.0,5)") == false)
    assert(VersionRange.versionSatisfiesRange("5.0", "[4.1.0,5)") == false)
    assert(VersionRange.versionSatisfiesRange("5.0.0", "[4.1.0,5)") == false)
  }

  it should "return false for version below range" in {
    assert(VersionRange.versionSatisfiesRange("4.0.9", "[4.1.0,5)") == false)
    assert(VersionRange.versionSatisfiesRange("3.0.0", "[4.1.0,5)") == false)
  }

  it should "return false for version above range" in {
    assert(VersionRange.versionSatisfiesRange("5.0.1", "[4.1.0,5)") == false)
    assert(VersionRange.versionSatisfiesRange("6.0.0", "[4.1.0,5)") == false)
  }

  it should "handle fully inclusive range [1.0,2.0]" in {
    assert(VersionRange.versionSatisfiesRange("1.0", "[1.0,2.0]") == true)
    assert(VersionRange.versionSatisfiesRange("1.5", "[1.0,2.0]") == true)
    assert(VersionRange.versionSatisfiesRange("2.0", "[1.0,2.0]") == true)
    assert(VersionRange.versionSatisfiesRange("2.0.1", "[1.0,2.0]") == false)
    assert(VersionRange.versionSatisfiesRange("0.9", "[1.0,2.0]") == false)
  }

  it should "handle fully exclusive range (1.0,2.0)" in {
    assert(VersionRange.versionSatisfiesRange("1.0", "(1.0,2.0)") == false)
    assert(VersionRange.versionSatisfiesRange("1.0.1", "(1.0,2.0)") == true)
    assert(VersionRange.versionSatisfiesRange("1.5", "(1.0,2.0)") == true)
    assert(VersionRange.versionSatisfiesRange("1.9.9", "(1.0,2.0)") == true)
    assert(VersionRange.versionSatisfiesRange("2.0", "(1.0,2.0)") == false)
  }

  it should "handle open upper bound [1.0,)" in {
    assert(VersionRange.versionSatisfiesRange("1.0", "[1.0,)") == true)
    assert(VersionRange.versionSatisfiesRange("1.5", "[1.0,)") == true)
    assert(VersionRange.versionSatisfiesRange("100.0", "[1.0,)") == true)
    assert(VersionRange.versionSatisfiesRange("0.9", "[1.0,)") == false)
  }

  // Exact reproduction case from issue #3978 comment by eed3si9n
  it should "handle angular-bootstrap reproduction case [1.3.0,)" in {
    assert(VersionRange.versionSatisfiesRange("1.4.7", "[1.3.0,)") == true)
    assert(VersionRange.versionSatisfiesRange("1.3.0", "[1.3.0,)") == true)
    assert(VersionRange.versionSatisfiesRange("1.2.9", "[1.3.0,)") == false)
  }

  it should "handle open lower bound (,2.0]" in {
    assert(VersionRange.versionSatisfiesRange("0.1", "(,2.0]") == true)
    assert(VersionRange.versionSatisfiesRange("1.0", "(,2.0]") == true)
    assert(VersionRange.versionSatisfiesRange("2.0", "(,2.0]") == true)
    assert(VersionRange.versionSatisfiesRange("2.0.1", "(,2.0]") == false)
  }

  it should "handle plus ranges like 1.0+" in {
    assert(VersionRange.versionSatisfiesRange("1.0", "1.0+") == true)
    assert(VersionRange.versionSatisfiesRange("1.1", "1.0+") == true)
    assert(VersionRange.versionSatisfiesRange("2.0", "1.0+") == true)
    assert(VersionRange.versionSatisfiesRange("0.9", "1.0+") == false)
  }

  it should "handle exact version (not a range)" in {
    assert(VersionRange.versionSatisfiesRange("1.0", "1.0") == true)
    assert(VersionRange.versionSatisfiesRange("1.0.0", "1.0") == false)
    assert(VersionRange.versionSatisfiesRange("1.1", "1.0") == false)
  }

  it should "handle single version constraint [1.0]" in {
    assert(VersionRange.versionSatisfiesRange("1.0", "[1.0]") == true)
    // Note: 1.0.0 is considered equal to 1.0 in semantic version comparison
    assert(VersionRange.versionSatisfiesRange("1.0.0", "[1.0]") == true)
    assert(VersionRange.versionSatisfiesRange("1.1", "[1.0]") == false)
  }

  // Exact reproduction case from issue #6244 (net.minidev:json-smart, [1.3.1,2.3] -> 2.3 selected)
  it should "not treat 2.3 as evicted when range is [1.3.1,2.3] (fixes #6244)" in {
    assert(VersionRange.isVersionRange("[1.3.1,2.3]") == true)
    assert(VersionRange.versionSatisfiesRange("2.3", "[1.3.1,2.3]") == true)
    assert(VersionRange.versionSatisfiesRange("1.3.1", "[1.3.1,2.3]") == true)
    assert(VersionRange.versionSatisfiesRange("2.4", "[1.3.1,2.3]") == false)
  }

  it should "handle comma-separated range without brackets (fixes #6244)" in {
    assert(VersionRange.isVersionRange("1.3.1,2.3") == true)
    assert(VersionRange.versionSatisfiesRange("2.3", "1.3.1,2.3") == true)
    assert(VersionRange.versionSatisfiesRange("1.3.1", "1.3.1,2.3") == true)
    assert(VersionRange.versionSatisfiesRange("2.4", "1.3.1,2.3") == false)
  }
}
