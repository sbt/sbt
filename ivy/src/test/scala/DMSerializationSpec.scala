package sbt

import org.specs2._
import matcher.MatchResult
import java.net.URL
import java.io.File
import sbt.serialization._

class DMSerializationSpec extends Specification {
  def is = sequential ^ s2"""

  This is a specification to check the serialization of dependency graph.

  CrossVersion.full should
    ${roundtripStr(CrossVersion.full: sbt.CrossVersion)}
  CrossVersion.binary should
    ${roundtripStr(CrossVersion.binary: sbt.CrossVersion)}
  CrossVersion.Disabled should
    ${roundtrip(CrossVersion.Disabled: sbt.CrossVersion)}

  Artifact("foo") should
    ${roundtrip(Artifact("foo"))}
  Artifact("foo", "sources") should
    ${roundtrip(Artifact("foo", "sources"))}
  Artifact.pom("foo") should
    ${roundtrip(Artifact.pom("foo"))}
  Artifact("foo", url("http://example.com/")) should
    ${roundtrip(Artifact("foo", new URL("http://example.com/")))}
  Artifact("foo").extra(("key", "value")) should
    ${roundtrip(Artifact("foo").extra(("key", "value")))}

  ModuleID("org", "name", "1.0") should
    ${roundtrip(ModuleID("org", "name", "1.0"))}

  ModuleReport(ModuleID("org", "name", "1.0"), Nil, Nil) should
    ${roundtripStr(ModuleReport(ModuleID("org", "name", "1.0"), Nil, Nil))}
  Organization artifact report should
    ${roundtripStr(organizationArtifactReportExample)}
  Configuration report should
    ${roundtripStr(configurationReportExample)}
  Update report should
    ${roundtripStr(updateReportExample)}
  """

  lazy val updateReportExample =
    new UpdateReport(
      new File("./foo"),
      Vector(configurationReportExample),
      new UpdateStats(0, 0, 0, false),
      Map(new File("./foo") -> 0)
    )
  lazy val configurationReportExample =
    new ConfigurationReport(
      "compile",
      Vector(moduleReportExample),
      Vector(organizationArtifactReportExample),
      Nil
    )
  lazy val organizationArtifactReportExample =
    new OrganizationArtifactReport("org", "name", Vector(moduleReportExample))
  lazy val moduleReportExample =
    ModuleReport(ModuleID("org", "name", "1.0"), Nil, Nil)

  def roundtrip[A: Pickler: Unpickler](a: A) =
    roundtripBuilder(a) { _ must_== _ }
  def roundtripStr[A: Pickler: Unpickler](a: A) =
    roundtripBuilder(a) { _.toString must_== _.toString }
  def roundtripBuilder[A: Pickler: Unpickler](a: A)(f: (A, A) => MatchResult[Any]): MatchResult[Any] = {
    val json = toJsonString(a)
    println(json)
    val obj = fromJsonString[A](json).get
    f(a, obj)
  }
}
