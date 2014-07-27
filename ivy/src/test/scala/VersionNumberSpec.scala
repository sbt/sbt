package sbt

import org.specs2._

class VersionNumberSpec extends Specification {
  def is = s2"""

  This is a specification to check the version number parsing.

  1 should
    ${beParsedAs("1", Seq(1), Seq(), Seq())}
    ${breakDownTo("1", Some(1))}

  1.0 should
    ${beParsedAs("1.0", Seq(1, 0), Seq(), Seq())}
    ${breakDownTo("1.0", Some(1), Some(0))}

  1.0.0 should
    ${beParsedAs("1.0.0", Seq(1, 0, 0), Seq(), Seq())}
    ${breakDownTo("1.0.0", Some(1), Some(0), Some(0))}
    
    ${beSemVerCompatWith("1.0.0", "1.0.1")}
    ${beSemVerCompatWith("1.0.0", "1.1.1")}
    ${notBeSemVerCompatWith("1.0.0", "2.0.0")}
    ${notBeSemVerCompatWith("1.0.0", "1.0.0-M1")}

    ${beSecSegCompatWith("1.0.0", "1.0.1")}
    ${notBeSecSegCompatWith("1.0.0", "1.1.1")}
    ${notBeSecSegCompatWith("1.0.0", "2.0.0")}
    ${notBeSecSegCompatWith("1.0.0", "1.0.0-M1")}

  1.0.0.0 should
    ${beParsedAs("1.0.0.0", Seq(1, 0, 0, 0), Seq(), Seq())}
    ${breakDownTo("1.0.0.0", Some(1), Some(0), Some(0), Some(0))}

  0.12.0 should
    ${beParsedAs("0.12.0", Seq(0, 12, 0), Seq(), Seq())}
    ${breakDownTo("0.12.0", Some(0), Some(12), Some(0))}    

    ${notBeSemVerCompatWith("0.12.0", "0.12.0-RC1")}
    ${notBeSemVerCompatWith("0.12.0", "0.12.1")}
    ${notBeSemVerCompatWith("0.12.0", "0.12.1-M1")}

    ${notBeSecSegCompatWith("0.12.0", "0.12.0-RC1")}
    ${beSecSegCompatWith("0.12.0", "0.12.1")}
    ${beSecSegCompatWith("0.12.0", "0.12.1-M1")}

  0.1.0-SNAPSHOT should
    ${beParsedAs("0.1.0-SNAPSHOT", Seq(0, 1, 0), Seq("SNAPSHOT"), Seq())}

    ${beSemVerCompatWith("0.1.0-SNAPSHOT", "0.1.0-SNAPSHOT")}
    ${notBeSemVerCompatWith("0.1.0-SNAPSHOT", "0.1.0")}
    ${beSemVerCompatWith("0.1.0-SNAPSHOT", "0.1.0-SNAPSHOT+001")}

    ${beSecSegCompatWith("0.1.0-SNAPSHOT", "0.1.0-SNAPSHOT")}
    ${notBeSecSegCompatWith("0.1.0-SNAPSHOT", "0.1.0")}
    ${beSecSegCompatWith("0.1.0-SNAPSHOT", "0.1.0-SNAPSHOT+001")}

  0.1.0-M1 should
    ${beParsedAs("0.1.0-M1", Seq(0, 1, 0), Seq("M1"), Seq())}

  0.1.0-RC1 should
    ${beParsedAs("0.1.0-RC1", Seq(0, 1, 0), Seq("RC1"), Seq())}

  0.1.0-MSERVER-1 should
    ${beParsedAs("0.1.0-MSERVER-1", Seq(0, 1, 0), Seq("MSERVER", "1"), Seq())}

  2.10.4-20140115-000117-b3a-sources should
    ${beParsedAs("2.10.4-20140115-000117-b3a-sources", Seq(2, 10, 4), Seq("20140115", "000117", "b3a", "sources"), Seq())}

    ${beSemVerCompatWith("2.10.4-20140115-000117-b3a-sources", "2.0.0")}

    ${notBeSecSegCompatWith("2.10.4-20140115-000117-b3a-sources", "2.0.0")}

  20140115000117-b3a-sources should
    ${beParsedAs("20140115000117-b3a-sources", Seq(20140115000117L), Seq("b3a", "sources"), Seq())}

  1.0.0-alpha+001+002 should
    ${beParsedAs("1.0.0-alpha+001+002", Seq(1, 0, 0), Seq("alpha"), Seq("+001", "+002"))}

  non.space.!?string should
    ${beParsedAs("non.space.!?string", Seq(), Seq(), Seq("non.space.!?string"))}

  space !?string should
    ${beParsedAsError("space !?string")}

  blank string should
    ${beParsedAsError("")} 
                                                                """

  def beParsedAs(s: String, ns: Seq[Long], ts: Seq[String], es: Seq[String]) =
    s match {
      case VersionNumber(ns1, ts1, es1) if (ns1 == ns && ts1 == ts && es1 == es) =>
        (VersionNumber(ns, ts, es).toString must_== s) and
          (VersionNumber(ns, ts, es) == VersionNumber(ns, ts, es))
      case VersionNumber(ns1, ts1, es1) =>
        sys.error(s"$ns1, $ts1, $es1")
    }
  def breakDownTo(s: String, major: Option[Long], minor: Option[Long] = None,
    patch: Option[Long] = None, buildNumber: Option[Long] = None) =
    s match {
      case VersionNumber(ns, ts, es) =>
        val v = VersionNumber(ns, ts, es)
        (v._1 must_== major) and
          (v._2 must_== minor) and
          (v._3 must_== patch) and
          (v._4 must_== buildNumber)
    }
  def beParsedAsError(s: String) =
    s match {
      case VersionNumber(ns1, ts1, es1) => failure
      case _                            => success
    }
  def beSemVerCompatWith(v1: String, v2: String) =
    VersionNumber.SemVer.isCompatible(VersionNumber(v1), VersionNumber(v2)) must_== true
  def notBeSemVerCompatWith(v1: String, v2: String) =
    VersionNumber.SemVer.isCompatible(VersionNumber(v1), VersionNumber(v2)) must_== false
  def beSecSegCompatWith(v1: String, v2: String) =
    VersionNumber.SecondSegment.isCompatible(VersionNumber(v1), VersionNumber(v2)) must_== true
  def notBeSecSegCompatWith(v1: String, v2: String) =
    VersionNumber.SecondSegment.isCompatible(VersionNumber(v1), VersionNumber(v2)) must_== false
}
