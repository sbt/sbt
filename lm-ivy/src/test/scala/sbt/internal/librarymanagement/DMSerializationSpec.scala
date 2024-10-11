package sbt.internal.librarymanagement

import java.net.URI
import java.io.File

import sbt.librarymanagement._
import sjsonnew.shaded.scalajson.ast.unsafe._
import sjsonnew._, support.scalajson.unsafe._
import LibraryManagementCodec._
import verify.BasicTestSuite

object DMSerializationSpec extends BasicTestSuite {
  test("CrossVersion.full should roundtrip") {
    roundtripStr(CrossVersion.full: CrossVersion)
  }

  test("CrossVersion.binary should roundtrip") {
    roundtripStr(CrossVersion.binary: CrossVersion)
  }

  test("CrossVersion.for3Use2_13 should roundtrip") {
    roundtripStr(CrossVersion.for3Use2_13: CrossVersion)
  }

  test("CrossVersion.for2_13Use3 with prefix should roundtrip") {
    roundtripStr(CrossVersion.for2_13Use3With("_sjs1", ""): CrossVersion)
  }

  test("CrossVersion.Disabled should roundtrip") {
    roundtrip(Disabled(): CrossVersion)
  }

  test("""Artifact("foo") should roundtrip""") {
    roundtrip(Artifact("foo"))
  }

  test("""Artifact("foo", "sources") should roundtrip""") {
    roundtrip(Artifact("foo", "sources"))
  }

  test("""Artifact.pom("foo") should roundtrip""") {
    roundtrip(Artifact.pom("foo"))
  }

  test("""Artifact("foo", url("http://example.com/")) should roundtrip""") {
    roundtrip(Artifact("foo", new URI("http://example.com/")))
  }

  test("""Artifact("foo").extra(("key", "value")) should roundtrip""") {
    roundtrip(Artifact("foo").extra(("key", "value")))
  }

  test("""ModuleID("org", "name", "1.0") should roundtrip""") {
    roundtrip(ModuleID("org", "name", "1.0"))
  }

  test("""ModuleReport(ModuleID("org", "name", "1.0"), Nil, Nil) should roundtrip""") {
    roundtripStr(ModuleReport(ModuleID("org", "name", "1.0"), Vector.empty, Vector.empty))
  }

  test("Organization artifact report should roundtrip") {
    roundtripStr(organizationArtifactReportExample)
  }

  test("Configuration report should roundtrip") {
    roundtripStr(configurationReportExample)
  }

  test("Update report should roundtrip") {
    roundtripStr(updateReportExample)
  }

  lazy val updateReportExample =
    UpdateReport(
      new File("./foo"),
      Vector(configurationReportExample),
      UpdateStats(0, 0, 0, false),
      Map("./foo" -> 0)
    )
  lazy val configurationReportExample =
    ConfigurationReport(
      ConfigRef("compile"),
      Vector(moduleReportExample),
      Vector(organizationArtifactReportExample)
    )
  lazy val organizationArtifactReportExample =
    OrganizationArtifactReport("org", "name", Vector(moduleReportExample))
  lazy val moduleReportExample =
    ModuleReport(ModuleID("org", "name", "1.0"), Vector.empty, Vector.empty)

  def roundtrip[A: JsonReader: JsonWriter](a: A): Unit =
    roundtripBuilder(a) { (x1, x2) =>
      assert(x1 == x2)
    }

  def roundtripStr[A: JsonReader: JsonWriter](a: A): Unit =
    roundtripBuilder(a) { (x1, x2) =>
      assert(x1.toString == x2.toString)
    }

  def roundtripBuilder[A: JsonReader: JsonWriter](a: A)(f: (A, A) => Unit): Unit = {
    val json = isoString to (Converter toJsonUnsafe a)
    println(json)
    val obj = Converter.fromJsonUnsafe[A](isoString from json)
    f(a, obj)
  }

  implicit val isoString: IsoString[JValue] =
    IsoString.iso(CompactPrinter.apply, Parser.parseUnsafe)
}
