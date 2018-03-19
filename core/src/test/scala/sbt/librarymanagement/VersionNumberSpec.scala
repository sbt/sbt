package sbt.librarymanagement

import sbt.internal.librarymanagement.UnitSpec
import org.scalatest.Inside

// This is a specification to check VersionNumber and VersionNumberCompatibility.
class VersionNumberSpec extends UnitSpec with Inside {
  import VersionNumber.{ SemVer, SecondSegment }

  version("1") { v =>
    parsesTo(Seq(1), Seq(), Seq())(v)
    breaksDownTo(Some(1))(v)
    cascadesTo(Seq("1"))(v)
  }

  version("1.0") { v =>
    parsesTo(Seq(1, 0), Seq(), Seq())(v)
    breaksDownTo(Some(1), Some(0))(v)
    cascadesTo(Seq("1.0"))(v)
  }

  version("1.0.0") { v =>
    parsesTo(Seq(1, 0, 0), Seq(), Seq())(v)
    breaksDownTo(Some(1), Some(0), Some(0))(v)
    cascadesTo(Seq("1.0.0", "1.0"))(v)

    isCompatibleWith("1.0.1", SemVer)(v)
    isCompatibleWith("1.1.1", SemVer)(v)
    isNotCompatibleWith("2.0.0", SemVer)(v)
    isNotCompatibleWith("1.0.0-M1", SemVer)(v)

    isCompatibleWith("1.0.1", SecondSegment)(v)
    isNotCompatibleWith("1.1.1", SecondSegment)(v)
    isNotCompatibleWith("2.0.0", SecondSegment)(v)
    isNotCompatibleWith("1.0.0-M1", SecondSegment)(v)
  }

  version("1.0.0.0") { v =>
    parsesTo(Seq(1, 0, 0, 0), Seq(), Seq())(v)
    breaksDownTo(Some(1), Some(0), Some(0), Some(0))(v)
    cascadesTo(Seq("1.0.0.0", "1.0.0", "1.0"))(v)
  }

  version("0.12.0") { v =>
    parsesTo(Seq(0, 12, 0), Seq(), Seq())(v)
    breaksDownTo(Some(0), Some(12), Some(0))(v)
    cascadesTo(Seq("0.12.0", "0.12"))(v)

    isNotCompatibleWith("0.12.0-RC1", SemVer)(v)
    isNotCompatibleWith("0.12.1", SemVer)(v)
    isNotCompatibleWith("0.12.1-M1", SemVer)(v)

    isNotCompatibleWith("0.12.0-RC1", SecondSegment)(v)
    isCompatibleWith("0.12.1", SecondSegment)(v)
    isCompatibleWith("0.12.1-M1", SecondSegment)(v)
  }

  version("0.1.0-SNAPSHOT") { v =>
    parsesTo(Seq(0, 1, 0), Seq("SNAPSHOT"), Seq())(v)
    cascadesTo(Seq("0.1.0-SNAPSHOT", "0.1.0", "0.1"))(v)

    isCompatibleWith("0.1.0-SNAPSHOT", SemVer)(v)
    isNotCompatibleWith("0.1.0", SemVer)(v)
    isCompatibleWith("0.1.0-SNAPSHOT+001", SemVer)(v)

    isCompatibleWith("0.1.0-SNAPSHOT", SecondSegment)(v)
    isNotCompatibleWith("0.1.0", SecondSegment)(v)
    isCompatibleWith("0.1.0-SNAPSHOT+001", SecondSegment)(v)
  }

  version("0.1.0-M1") { v =>
    parsesTo(Seq(0, 1, 0), Seq("M1"), Seq())(v)
    cascadesTo(Seq("0.1.0-M1", "0.1.0", "0.1"))(v)
  }

  version("0.1.0-RC1") { v =>
    parsesTo(Seq(0, 1, 0), Seq("RC1"), Seq())(v)
    cascadesTo(Seq("0.1.0-RC1", "0.1.0", "0.1"))(v)
  }

  version("0.1.0-MSERVER-1") { v =>
    parsesTo(Seq(0, 1, 0), Seq("MSERVER", "1"), Seq())(v)
    cascadesTo(Seq("0.1.0-MSERVER-1", "0.1.0", "0.1"))(v)
  }

  version("2.10.4-20140115-000117-b3a-sources") { v =>
    parsesTo(Seq(2, 10, 4), Seq("20140115", "000117", "b3a", "sources"), Seq())(v)
    cascadesTo(Seq("2.10.4-20140115-000117-b3a-sources", "2.10.4", "2.10"))(v)
    isCompatibleWith("2.0.0", SemVer)(v)
    isNotCompatibleWith("2.0.0", SecondSegment)(v)
  }

  version("20140115000117-b3a-sources") { v =>
    parsesTo(Seq(20140115000117L), Seq("b3a", "sources"), Seq())(v)
    cascadesTo(Seq("20140115000117-b3a-sources"))(v)
  }

  version("1.0.0-alpha+001+002") { v =>
    parsesTo(Seq(1, 0, 0), Seq("alpha"), Seq("+001", "+002"))(v)
    cascadesTo(Seq("1.0.0-alpha+001+002", "1.0.0", "1.0"))(v)
  }

  version("non.space.!?string") { v =>
    parsesTo(Seq(), Seq(), Seq("non.space.!?string"))(v)
    cascadesTo(Seq("non.space.!?string"))(v)
  }

  version("space !?string") { v =>
    parsesToError(v)
  }
  version("") { v =>
    parsesToError(v)
  }

  ////

  final class VersionString(val value: String)

  private[this] def version[A](s: String)(f: VersionString => A) = {
    behavior of s"""Version "$s""""
    f(new VersionString(s))
  }

  def parsesTo(ns: Seq[Long], ts: Seq[String], es: Seq[String])(implicit v: VersionString): Unit =
    it should s"parse to ($ns, $ts, $es)" in inside(v.value) {
      case VersionNumber(ns1, ts1, es1) =>
        (ns1 shouldBe ns)
        (ts1 shouldBe ts)
        (es1 shouldBe es)
        (VersionNumber(ns, ts, es).toString shouldBe v.value)
        (VersionNumber(ns, ts, es) shouldBe VersionNumber(ns, ts, es))
    }

  private[this] def parsesToError(implicit v: VersionString): Unit =
    it should "parse as an error" in {
      v.value should not matchPattern {
        case s: String if VersionNumber.unapply(s).isDefined => // because of unapply overloading
      }
    }

  private[this] def breaksDownTo(
      major: Option[Long],
      minor: Option[Long] = None,
      patch: Option[Long] = None,
      buildNumber: Option[Long] = None
  )(implicit v: VersionString): Unit =
    it should s"breakdown to ($major, $minor, $patch, $buildNumber)" in inside(v.value) {
      case VersionNumber(ns, ts, es) =>
        val v = VersionNumber(ns, ts, es)
        (v._1 shouldBe major)
        (v._2 shouldBe minor)
        (v._3 shouldBe patch)
        (v._4 shouldBe buildNumber)
    }

  private[this] def cascadesTo(ns: Seq[String])(implicit v: VersionString) = {
    it should s"cascade to $ns" in {
      val versionNumbers = ns.toVector map VersionNumber.apply
      VersionNumber(v.value).cascadingVersions shouldBe versionNumbers
    }
  }

  def isCompatibleWith(v2: String, vnc: VersionNumberCompatibility)(implicit v1: VersionString) =
    checkCompat(true, vnc, v2)

  def isNotCompatibleWith(v2: String, vnc: VersionNumberCompatibility)(implicit v1: VersionString) =
    checkCompat(false, vnc, v2)

  private[this] def checkCompat(
      expectOutcome: Boolean,
      vnc: VersionNumberCompatibility,
      v2: String
  )(implicit v1: VersionString) = {
    val prefix = if (expectOutcome) "" else "NOT "
    val compatibilityStrategy = vnc match {
      case SemVer        => "SemVer"
      case SecondSegment => "SecondSegment"
      case _             => val s = vnc.name; if (s contains " ") s""""$s"""" else s
    }
    it should s"${prefix}be $compatibilityStrategy compatible with $v2" in {
      vnc.isCompatible(VersionNumber(v1.value), VersionNumber(v2)) shouldBe expectOutcome
    }
  }
}
