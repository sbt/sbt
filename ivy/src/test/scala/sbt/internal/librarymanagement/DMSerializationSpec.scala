package sbt.internal.librarymanagement

import java.net.URL
import java.io.File

import sbt.librarymanagement._
import sjsonnew.shaded.scalajson.ast.unsafe._
import sjsonnew._, support.scalajson.unsafe._
import org.scalatest.Assertion
import LibraryManagementCodec._

class DMSerializationSpec extends UnitSpec {
  "CrossVersion.full" should "roundtrip" in {
    roundtripStr(CrossVersion.full: CrossVersion)
  }
  "CrossVersion.binary" should "roundtrip" in {
    roundtripStr(CrossVersion.binary: CrossVersion)
  }
  "CrossVersion.Disabled" should "roundtrip" in {
    roundtrip(Disabled(): CrossVersion)
  }
  """Artifact("foo")""" should "roundtrip" in {
    roundtrip(Artifact("foo"))
  }
  """Artifact("foo", "sources")""" should "roundtrip" in {
    roundtrip(Artifact("foo", "sources"))
  }
  """Artifact.pom("foo")""" should "roundtrip" in {
    roundtrip(Artifact.pom("foo"))
  }
  """Artifact("foo", url("http://example.com/"))""" should "roundtrip" in {
    roundtrip(Artifact("foo", new URL("http://example.com/")))
  }
  """Artifact("foo").extra(("key", "value"))""" should "roundtrip" in {
    roundtrip(Artifact("foo").extra(("key", "value")))
  }
  """ModuleID("org", "name", "1.0")""" should "roundtrip" in {
    roundtrip(ModuleID("org", "name", "1.0"))
  }
  """ModuleReport(ModuleID("org", "name", "1.0"), Nil, Nil)""" should "roundtrip" in {
    roundtripStr(ModuleReport(ModuleID("org", "name", "1.0"), Vector.empty, Vector.empty))
  }
  "Organization artifact report" should "roundtrip" in {
    roundtripStr(organizationArtifactReportExample)
  }
  "Configuration report" should "roundtrip" in {
    roundtripStr(configurationReportExample)
  }
  "Update report" should "roundtrip" in {
    roundtripStr(updateReportExample)
  }

  lazy val updateReportExample =
    UpdateReport(new File("./foo"),
                 Vector(configurationReportExample),
                 UpdateStats(0, 0, 0, false),
                 Map(new File("./foo") -> 0))
  lazy val configurationReportExample =
    ConfigurationReport(ConfigRef("compile"),
                        Vector(moduleReportExample),
                        Vector(organizationArtifactReportExample))
  lazy val organizationArtifactReportExample =
    OrganizationArtifactReport("org", "name", Vector(moduleReportExample))
  lazy val moduleReportExample =
    ModuleReport(ModuleID("org", "name", "1.0"), Vector.empty, Vector.empty)

  def roundtrip[A: JsonReader: JsonWriter](a: A): Assertion =
    roundtripBuilder(a) { _ shouldBe _ }

  def roundtripStr[A: JsonReader: JsonWriter](a: A): Assertion =
    roundtripBuilder(a) { _.toString shouldBe _.toString }

  def roundtripBuilder[A: JsonReader: JsonWriter](a: A)(f: (A, A) => Assertion): Assertion = {
    val json = isoString to (Converter toJsonUnsafe a)
    println(json)
    val obj = Converter fromJsonUnsafe [A] (isoString from json)
    f(a, obj)
  }

  implicit val isoString: IsoString[JValue] =
    IsoString.iso(CompactPrinter.apply, FixedParser.parseUnsafe)
}
