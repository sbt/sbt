package sbt.internal.librarymanagement

import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor

import sbt.internal.librarymanagement.IvyScalaUtil.OverrideScalaMediator
import sbt.librarymanagement._
import sbt.librarymanagement.ScalaArtifacts._
import verify.BasicTestSuite

object ScalaOverrideTest extends BasicTestSuite {
  val OtherOrgID = "other.org"

  def check(org0: String, version0: String)(org1: String, name1: String, version1: String) = {
    val scalaConfigs = Configurations.default.toVector filter { Configurations.underScalaVersion } map {
      _.name
    }
    val osm = new OverrideScalaMediator(org0, version0, scalaConfigs)

    val mrid = ModuleRevisionId.newInstance(org1, name1, version1)
    val dd = new DefaultDependencyDescriptor(mrid, false)
    dd.addDependencyConfiguration("compile", "compile")

    val res = osm.mediate(dd)
    assert(res.getDependencyRevisionId == ModuleRevisionId.newInstance(org0, name1, version0))
  }

  test("""OverrideScalaMediator should override compiler version""") {
    check(Organization, "2.11.8")(
      Organization,
      CompilerID,
      "2.11.9"
    )
  }

  test("it should override library version") {
    check(Organization, "2.11.8")(
      Organization,
      LibraryID,
      "2.11.8"
    )
  }

  test("it should override reflect version") {
    check(Organization, "2.11.8")(
      Organization,
      ReflectID,
      "2.11.7"
    )
  }

  test("it should override actors version") {
    check(Organization, "2.11.8")(
      Organization,
      ActorsID,
      "2.11.6"
    )
  }

  test("it should override scalap version") {
    check(Organization, "2.11.8")(
      Organization,
      ScalapID,
      "2.11.5"
    )
  }

  test("it should override default compiler organization") {
    check(OtherOrgID, "2.11.8")(
      Organization,
      CompilerID,
      "2.11.9"
    )
  }

  test("it should override default library organization") {
    check(OtherOrgID, "2.11.8")(
      Organization,
      LibraryID,
      "2.11.8"
    )
  }

  test("it should override default reflect organization") {
    check(OtherOrgID, "2.11.8")(
      Organization,
      ReflectID,
      "2.11.7"
    )
  }

  test("it should override default actors organization") {
    check(OtherOrgID, "2.11.8")(
      Organization,
      ActorsID,
      "2.11.6"
    )
  }

  test("it should override default scalap organization") {
    check(OtherOrgID, "2.11.8")(
      Organization,
      ScalapID,
      "2.11.5"
    )
  }

  test("it should override custom compiler organization") {
    check(Organization, "2.11.8")(
      OtherOrgID,
      CompilerID,
      "2.11.9"
    )
  }

  test("it should override custom library organization") {
    check(Organization, "2.11.8")(
      OtherOrgID,
      LibraryID,
      "2.11.8"
    )
  }

  test("it should override custom reflect organization") {
    check(Organization, "2.11.8")(
      OtherOrgID,
      ReflectID,
      "2.11.7"
    )
  }

  test("it should override custom actors organization") {
    check(Organization, "2.11.8")(
      OtherOrgID,
      ActorsID,
      "2.11.6"
    )
  }

  test("it should override custom scalap organization") {
    check(Organization, "2.11.8")(
      OtherOrgID,
      ScalapID,
      "2.11.5"
    )
  }
}
