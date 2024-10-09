package sbt.internal.librarymanagement

import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor

import sbt.internal.librarymanagement.IvyScalaUtil.OverrideScalaMediator
import sbt.librarymanagement._
import sbt.librarymanagement.ScalaArtifacts._
import verify.BasicTestSuite

object ScalaOverrideTest extends BasicTestSuite {
  /*
  val OtherOrgID = "other.org"

  private val scalaConfigs =
    Configurations.default.filter(Configurations.underScalaVersion).map(_.name)

  def checkOrgAndVersion(
      org0: String,
      version0: String
  )(org1: String, name1: String, version1: String): Unit = {
    val osm = new OverrideScalaMediator(org0, version0, scalaConfigs)

    val mrid = ModuleRevisionId.newInstance(org1, name1, version1)
    val dd = new DefaultDependencyDescriptor(mrid, false)
    dd.addDependencyConfiguration("compile", "compile")

    val res = osm.mediate(dd)
    assert(res.getDependencyRevisionId == ModuleRevisionId.newInstance(org0, name1, version0))
  }

  def checkOnlyOrg(
      org0: String,
      version0: String
  )(org1: String, name1: String, version1: String): Unit = {
    val osm = new OverrideScalaMediator(org0, version0, scalaConfigs)

    val mrid = ModuleRevisionId.newInstance(org1, name1, version1)
    val dd = new DefaultDependencyDescriptor(mrid, false)
    dd.addDependencyConfiguration("compile", "compile")

    val res = osm.mediate(dd)
    assert(res.getDependencyRevisionId == ModuleRevisionId.newInstance(org0, name1, version1))
  }

  def checkNoOverride(
      org0: String,
      version0: String
  )(org1: String, name1: String, version1: String): Unit = {
    val osm = new OverrideScalaMediator(org0, version0, scalaConfigs)

    val mrid = ModuleRevisionId.newInstance(org1, name1, version1)
    val dd = new DefaultDependencyDescriptor(mrid, false)
    dd.addDependencyConfiguration("compile", "compile")

    val res = osm.mediate(dd)
    assert(res.getDependencyRevisionId == mrid)
  }

  test("OverrideScalaMediator should override compiler version") {
    checkOrgAndVersion(Organization, "2.11.8")(
      Organization,
      CompilerID,
      "2.11.9"
    )
  }

  test("it should override library version") {
    checkOrgAndVersion(Organization, "2.11.8")(
      Organization,
      LibraryID,
      "2.11.8"
    )
  }

  test("it should override reflect version") {
    checkOrgAndVersion(Organization, "2.11.8")(
      Organization,
      ReflectID,
      "2.11.7"
    )
  }

  test("it should override actors version") {
    checkOrgAndVersion(Organization, "2.11.8")(
      Organization,
      ActorsID,
      "2.11.6"
    )
  }

  test("it should override scalap version") {
    checkOrgAndVersion(Organization, "2.11.8")(
      Organization,
      ScalapID,
      "2.11.5"
    )
  }

  test("it should override default compiler organization") {
    checkOrgAndVersion(OtherOrgID, "2.11.8")(
      Organization,
      CompilerID,
      "2.11.9"
    )
  }

  test("it should override default library organization") {
    checkOrgAndVersion(OtherOrgID, "2.11.8")(
      Organization,
      LibraryID,
      "2.11.8"
    )
  }

  test("it should override default reflect organization") {
    checkOrgAndVersion(OtherOrgID, "2.11.8")(
      Organization,
      ReflectID,
      "2.11.7"
    )
  }

  test("it should override default actors organization") {
    checkOrgAndVersion(OtherOrgID, "2.11.8")(
      Organization,
      ActorsID,
      "2.11.6"
    )
  }

  test("it should override default scalap organization") {
    checkOrgAndVersion(OtherOrgID, "2.11.8")(
      Organization,
      ScalapID,
      "2.11.5"
    )
  }

  test("it should override custom compiler organization") {
    checkOrgAndVersion(Organization, "2.11.8")(
      OtherOrgID,
      CompilerID,
      "2.11.9"
    )
  }

  test("it should override custom library organization") {
    checkOrgAndVersion(Organization, "2.11.8")(
      OtherOrgID,
      LibraryID,
      "2.11.8"
    )
  }

  test("it should override custom reflect organization") {
    checkOrgAndVersion(Organization, "2.11.8")(
      OtherOrgID,
      ReflectID,
      "2.11.7"
    )
  }

  test("it should override custom actors organization") {
    checkOrgAndVersion(Organization, "2.11.8")(
      OtherOrgID,
      ActorsID,
      "2.11.6"
    )
  }

  test("it should override custom scalap organization") {
    checkOrgAndVersion(Organization, "2.11.8")(
      OtherOrgID,
      ScalapID,
      "2.11.5"
    )
  }

  test("it should override Scala 3 compiler version") {
    checkOrgAndVersion(Organization, "3.1.0")(
      Organization,
      Scala3CompilerPrefix + "3",
      "3.0.0"
    )
  }

  test("it should override Scala 3 library version") {
    checkOrgAndVersion(Organization, "3.1.0")(
      Organization,
      Scala3LibraryPrefix + "3",
      "3.0.0"
    )
  }

  test("it should override Scala 3 interfaces version") {
    checkOrgAndVersion(Organization, "3.1.0")(
      Organization,
      Scala3InterfacesID,
      "3.0.0"
    )
  }

  test("it should override TASTy core version") {
    checkOrgAndVersion(Organization, "3.1.0")(
      Organization,
      TastyCorePrefix + "3",
      "3.0.0"
    )
  }

  test("it should not override Scala 2 library version when using Scala 3") {
    checkNoOverride(Organization, "3.1.0")(
      Organization,
      LibraryID,
      "2.13.4"
    )
  }

  test("it should not override TASTy core version when using Scala 2") {
    checkNoOverride(Organization, "2.13.4")(
      Organization,
      TastyCorePrefix + "3",
      "3.0.0"
    )
  }

  test("it should override default Scala 3 compiler organization") {
    checkOrgAndVersion(OtherOrgID, "3.1.0")(
      Organization,
      Scala3CompilerPrefix + "3",
      "3.0.0"
    )
  }

  test("it should override default Scala 3 library organization") {
    checkOrgAndVersion(OtherOrgID, "3.1.0")(
      Organization,
      Scala3LibraryPrefix + "3",
      "3.0.0"
    )
  }

  test("it should override default Scala 3 interfaces organization") {
    checkOrgAndVersion(OtherOrgID, "3.1.0")(
      Organization,
      Scala3InterfacesID,
      "3.0.0"
    )
  }

  test("it should override default Scala 3 TASTy core organization") {
    checkOrgAndVersion(OtherOrgID, "3.1.0")(
      Organization,
      TastyCorePrefix + "3",
      "3.0.0"
    )
  }

  test("it should override default Scala 2 library organization when in Scala 3") {
    checkOnlyOrg(OtherOrgID, "3.1.0")(
      Organization,
      LibraryID,
      "2.13.4"
    )
  }

  test("it should override default TASTy core organization when in Scala 2") {
    checkOnlyOrg(OtherOrgID, "2.13.4")(
      Organization,
      TastyCorePrefix + "3",
      "3.0.0"
    )
  }
   */
}
