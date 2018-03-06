package sbt.librarymanagement

import org.scalatest.Assertion
import sbt.internal.librarymanagement.BaseIvySpecification
import sbt.librarymanagement.syntax._
import DependencyBuilders.OrganizationArtifactName

class InclExclSpec extends BaseIvySpecification {
  def createLiftDep(toExclude: ExclusionRule): ModuleID =
    ("net.liftweb" %% "lift-mapper" % "2.6-M4" % "compile").excludeAll(toExclude)

  def createMetaDep(toExclude: ExclusionRule): ModuleID =
    ("org.scalameta" %% "paradise" % "3.0.0-M8" % "compile")
      .cross(CrossVersion.full)
      .excludeAll(toExclude)

  def getIvyReport(dep: ModuleID, scalaVersion: Option[String]): UpdateReport = {
    cleanIvyCache()
    val ivyModule = module(defaultModuleId, Vector(dep), scalaVersion)
    ivyUpdate(ivyModule)
  }

  def testLiftJsonIsMissing(report: UpdateReport): Assertion = {
    assert(
      !report.allModules.exists(_.name.contains("lift-json")),
      "lift-json has not been excluded."
    )
  }

  def testScalaLibraryIsMissing(report: UpdateReport): Assertion = {
    assert(
      !report.allModules.exists(_.name.contains("scala-library")),
      "scala-library has not been excluded."
    )
  }

  def testScalahostIsMissing(report: UpdateReport): Assertion = {
    assert(
      !report.allModules.exists(_.name.contains("scalahost")),
      "scalahost has not been excluded."
    )
  }

  val scala210 = Some("2.10.4")
  it should "exclude any version of lift-json via a new exclusion rule" in {
    val toExclude = ExclusionRule("net.liftweb", "lift-json_2.10")
    val report = getIvyReport(createLiftDep(toExclude), scala210)
    testLiftJsonIsMissing(report)
  }

  it should "exclude any version of lift-json with explicit Scala version" in {
    val excluded: OrganizationArtifactName = "net.liftweb" % "lift-json_2.10"
    val report = getIvyReport(createLiftDep(excluded), scala210)
    testLiftJsonIsMissing(report)
  }

  it should "exclude any version of cross-built lift-json" in {
    val excluded: OrganizationArtifactName = "net.liftweb" %% "lift-json"
    val report = getIvyReport(createLiftDep(excluded), scala210)
    testLiftJsonIsMissing(report)
  }

  val scala2122 = Some("2.12.2")
  it should "exclude a concrete version of lift-json when it's full cross version" in {
    val excluded: ModuleID = ("org.scalameta" % "scalahost" % "1.7.0").cross(CrossVersion.full)
    val report = getIvyReport(createMetaDep(excluded), scala2122)
    testScalahostIsMissing(report)
  }

  it should "exclude any version of lift-json when it's full cross version" in {
    val excluded = new OrganizationArtifactName("net.liftweb", "lift-json", CrossVersion.full)
    val report = getIvyReport(createMetaDep(excluded), scala2122)
    testScalahostIsMissing(report)
  }

  it should "exclude any version of scala-library via * artifact id" in {
    val toExclude = ExclusionRule("org.scala-lang", "*")
    val report = getIvyReport(createLiftDep(toExclude), scala210)
    testScalaLibraryIsMissing(report)
  }

  it should "exclude any version of scala-library via * org id" in {
    val toExclude = ExclusionRule("*", "scala-library")
    val report = getIvyReport(createLiftDep(toExclude), scala210)
    testScalaLibraryIsMissing(report)
  }
}
