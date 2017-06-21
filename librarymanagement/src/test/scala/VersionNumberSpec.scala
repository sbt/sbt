package sbt.librarymanagement

import sbt.internal.librarymanagement.UnitSpec

// This is a specification to check the version number parsing.
class VersionNumberSpec extends UnitSpec {
  "1" should "be parsed" in beParsedAs("1", Seq(1), Seq(), Seq())
  it should "breakdown" in breakDownTo("1", Some(1))
  it should "cascade" in generateCorrectCascadingNumbers("1", Seq("1"))

  "1.0" should "be parsed" in beParsedAs("1.0", Seq(1, 0), Seq(), Seq())
  it should "breakdown" in breakDownTo("1.0", Some(1), Some(0))
  it should "cascade" in generateCorrectCascadingNumbers("1.0", Seq("1.0"))

  "1.0.0" should "be parsed" in beParsedAs("1.0.0", Seq(1, 0, 0), Seq(), Seq())
  it should "breakdown" in breakDownTo("1.0.0", Some(1), Some(0), Some(0))
  it should "cascade" in generateCorrectCascadingNumbers("1.0.0", Seq("1.0.0", "1.0"))
  it should "be SemVer compat with 1.0.1" in beSemVerCompatWith("1.0.0", "1.0.1")
  it should "be SemVer compat with 1.1.1" in beSemVerCompatWith("1.0.0", "1.1.1")
  it should "not be SemVer compat with 2.0.0" in notBeSemVerCompatWith("1.0.0", "2.0.0")
  it should "not be SemVer compat with 1.0.0-M1" in notBeSemVerCompatWith("1.0.0", "1.0.0-M1")
  it should "be SecSeg compat with 1.0.1" in beSecSegCompatWith("1.0.0", "1.0.1")
  it should "not be SecSeg compat with 1.1.1" in notBeSecSegCompatWith("1.0.0", "1.1.1")
  it should "not be SecSeg compat with 2.0.0" in notBeSecSegCompatWith("1.0.0", "2.0.0")
  it should "not be SecSeg compat with 1.0.0-M1" in notBeSecSegCompatWith("1.0.0", "1.0.0-M1")

  "1.0.0.0" should "be parsed" in beParsedAs("1.0.0.0", Seq(1, 0, 0, 0), Seq(), Seq())
  it should "breakdown" in breakDownTo("1.0.0.0", Some(1), Some(0), Some(0), Some(0))
  it should "cascade" in generateCorrectCascadingNumbers("1.0.0.0", Seq("1.0.0.0", "1.0.0", "1.0"))

  "0.12.0" should "be parsed" in beParsedAs("0.12.0", Seq(0, 12, 0), Seq(), Seq())
  it should "breakdown" in breakDownTo("0.12.0", Some(0), Some(12), Some(0))
  it should "cascade" in generateCorrectCascadingNumbers("0.12.0", Seq("0.12.0", "0.12"))
  it should "not be SemVer compat with 0.12.0-RC1" in notBeSemVerCompatWith("0.12.0", "0.12.0-RC1")
  it should "not be SemVer compat with 0.12.1" in notBeSemVerCompatWith("0.12.0", "0.12.1")
  it should "not be SemVer compat with 0.12.1-M1" in notBeSemVerCompatWith("0.12.0", "0.12.1-M1")
  it should "not be SecSeg compat with 0.12.0-RC1" in notBeSecSegCompatWith("0.12.0", "0.12.0-RC1")
  it should "be SecSeg compat with 0.12.1" in beSecSegCompatWith("0.12.0", "0.12.1")
  it should "be SecSeg compat with 0.12.1-M1" in beSecSegCompatWith("0.12.0", "0.12.1-M1")

  "0.1.0-SNAPSHOT" should "be parsed" in beParsedAs("0.1.0-SNAPSHOT",
                                                    Seq(0, 1, 0),
                                                    Seq("SNAPSHOT"),
                                                    Seq())
  it should "cascade" in generateCorrectCascadingNumbers("0.1.0-SNAPSHOT",
                                                         Seq("0.1.0-SNAPSHOT", "0.1.0", "0.1"))
  it should "be SemVer compat with 0.1.0-SNAPSHOT" in beSemVerCompatWith("0.1.0-SNAPSHOT",
                                                                         "0.1.0-SNAPSHOT")
  it should "not be SemVer compat with 0.1.0" in notBeSemVerCompatWith("0.1.0-SNAPSHOT", "0.1.0")
  it should "be SemVer compat with 0.1.0-SNAPSHOT+001" in beSemVerCompatWith("0.1.0-SNAPSHOT",
                                                                             "0.1.0-SNAPSHOT+001")
  it should "be SecSeg compat with 0.1.0-SNAPSHOT" in beSecSegCompatWith("0.1.0-SNAPSHOT",
                                                                         "0.1.0-SNAPSHOT")
  it should "be not SecSeg compat with 0.1.0" in notBeSecSegCompatWith("0.1.0-SNAPSHOT", "0.1.0")
  it should "be SecSeg compat with 0.1.0-SNAPSHOT+001" in beSecSegCompatWith("0.1.0-SNAPSHOT",
                                                                             "0.1.0-SNAPSHOT+001")

  "0.1.0-M1" should "be parsed" in beParsedAs("0.1.0-M1", Seq(0, 1, 0), Seq("M1"), Seq())
  it should "cascade" in generateCorrectCascadingNumbers("0.1.0-M1",
                                                         Seq("0.1.0-M1", "0.1.0", "0.1"))

  "0.1.0-RC1" should "be parsed" in beParsedAs("0.1.0-RC1", Seq(0, 1, 0), Seq("RC1"), Seq())
  it should "cascade" in generateCorrectCascadingNumbers("0.1.0-RC1",
                                                         Seq("0.1.0-RC1", "0.1.0", "0.1"))

  "0.1.0-MSERVER-1" should "be parsed" in beParsedAs("0.1.0-MSERVER-1",
                                                     Seq(0, 1, 0),
                                                     Seq("MSERVER", "1"),
                                                     Seq())
  it should "cascade" in generateCorrectCascadingNumbers("0.1.0-MSERVER-1",
                                                         Seq("0.1.0-MSERVER-1", "0.1.0", "0.1"))

  "2.10.4-20140115-000117-b3a-sources" should "be parsed" in {
    beParsedAs("2.10.4-20140115-000117-b3a-sources",
               Seq(2, 10, 4),
               Seq("20140115", "000117", "b3a", "sources"),
               Seq())
  }
  it should "cascade" in generateCorrectCascadingNumbers(
    "2.10.4-20140115-000117-b3a-sources",
    Seq("2.10.4-20140115-000117-b3a-sources", "2.10.4", "2.10"))
  it should "be SemVer compat with 2.0.0" in beSemVerCompatWith(
    "2.10.4-20140115-000117-b3a-sources",
    "2.0.0")
  it should "be not SecSeg compat with 2.0.0" in notBeSecSegCompatWith(
    "2.10.4-20140115-000117-b3a-sources",
    "2.0.0")

  "20140115000117-b3a-sources" should "be parsed" in {
    beParsedAs("20140115000117-b3a-sources", Seq(20140115000117L), Seq("b3a", "sources"), Seq())
  }
  it should "cascade" in generateCorrectCascadingNumbers("20140115000117-b3a-sources",
                                                         Seq("20140115000117-b3a-sources"))

  "1.0.0-alpha+001+002" should "be parsed" in {
    beParsedAs("1.0.0-alpha+001+002", Seq(1, 0, 0), Seq("alpha"), Seq("+001", "+002"))
  }
  it should "cascade" in generateCorrectCascadingNumbers(
    "1.0.0-alpha+001+002",
    Seq("1.0.0-alpha+001+002", "1.0.0", "1.0"))

  "non.space.!?string" should "be parsed" in {
    beParsedAs("non.space.!?string", Seq(), Seq(), Seq("non.space.!?string"))
  }
  it should "cascade" in generateCorrectCascadingNumbers("non.space.!?string",
                                                         Seq("non.space.!?string"))

  "space !?string" should "be parsed as an error" in beParsedAsError("space !?string")
  "blank string" should "be parsed as an error" in beParsedAsError("")

  def beParsedAs(s: String, ns: Seq[Long], ts: Seq[String], es: Seq[String]) =
    s match {
      case VersionNumber(ns1, ts1, es1) if (ns1 == ns && ts1 == ts && es1 == es) =>
        (VersionNumber(ns, ts, es).toString shouldBe s)
        (VersionNumber(ns, ts, es) shouldBe VersionNumber(ns, ts, es))
      case VersionNumber(ns1, ts1, es1) =>
        sys.error(s"$ns1, $ts1, $es1")
    }
  def breakDownTo(s: String,
                  major: Option[Long],
                  minor: Option[Long] = None,
                  patch: Option[Long] = None,
                  buildNumber: Option[Long] = None) =
    s match {
      case VersionNumber(ns, ts, es) =>
        val v = VersionNumber(ns, ts, es)
        (v._1 shouldBe major)
        (v._2 shouldBe minor)
        (v._3 shouldBe patch)
        (v._4 shouldBe buildNumber)
    }
  def beParsedAsError(s: String): Unit =
    s match {
      case VersionNumber(ns1, ts1, es1) => sys.error(s)
      case _                            => ()
    }
  def beSemVerCompatWith(v1: String, v2: String) =
    VersionNumber.SemVer.isCompatible(VersionNumber(v1), VersionNumber(v2)) shouldBe true
  def notBeSemVerCompatWith(v1: String, v2: String) =
    VersionNumber.SemVer.isCompatible(VersionNumber(v1), VersionNumber(v2)) shouldBe false
  def beSecSegCompatWith(v1: String, v2: String) =
    VersionNumber.SecondSegment.isCompatible(VersionNumber(v1), VersionNumber(v2)) shouldBe true
  def notBeSecSegCompatWith(v1: String, v2: String) =
    VersionNumber.SecondSegment.isCompatible(VersionNumber(v1), VersionNumber(v2)) shouldBe false
  def generateCorrectCascadingNumbers(s: String, ns: Seq[String]) = {
    val versionNumbers = ns.toVector map VersionNumber.apply
    VersionNumber(s).cascadingVersions shouldBe versionNumbers
  }
}
