package lmcoursier

import sbt.librarymanagement.*
import sbt.librarymanagement.syntax.*
import scala.annotation.nowarn
import TestKit.*

object InclExclSpec extends verify.BasicTestSuite {
  val scala210 = Some("2.10.4")
  test("it should exclude any version of lift-json via a new exclusion rule") {
    val toExclude = ExclusionRule("net.liftweb", "lift-json_2.10")
    val report = getUpdateReport(createLiftDep(toExclude), scala210)
    testLiftJsonIsMissing(report)
  }

  test("it should exclude any version of lift-json with explicit Scala version") {
    val excluded = "net.liftweb" % "lift-json_2.10"
    val report = getUpdateReport(createLiftDep(excluded), scala210)
    testLiftJsonIsMissing(report)
  }

  test("it should exclude any version of cross-built lift-json") {
    val excluded = "net.liftweb" %% "lift-json"
    val report = getUpdateReport(createLiftDep(excluded), scala210)
    testLiftJsonIsMissing(report)
  }

  test(
    "it should exclude any version of cross-built lift-json using `.exclude(String, String)` method with direct scala version definition"
  ) {
    @nowarn
    val liftDep =
      ("net.liftweb" %% "lift-mapper" % "2.6-M4" % "compile")
        .exclude("net.liftweb", "lift-json_2.10")
    val report = getUpdateReport(liftDep, scala210)
    testLiftJsonIsMissing(report)
  }

  test(
    "it should exclude any version of cross-built lift-json using `.exclude(OrganizationArtifactName)` method"
  ) {
    val liftDep =
      ("net.liftweb" %% "lift-mapper" % "2.6-M4" % "compile")
        .exclude("net.liftweb" %% "lift-json")
    val report = getUpdateReport(liftDep, scala210)
    testLiftJsonIsMissing(report)
  }

  test("it should exclude any version of cross-built lift-json using `.excludeAll` method") {
    val liftDep =
      ("net.liftweb" %% "lift-mapper" % "2.6-M4" % "compile")
        .excludeAll("net.liftweb" %% "lift-json")
    val report = getUpdateReport(liftDep, scala210)
    testLiftJsonIsMissing(report)
  }

  val scala2122 = Some("2.12.2")
  test("it should exclude a concrete version of lift-json when it's full cross version") {
    val excluded: ModuleID = ("org.scalameta" % "scalahost" % "1.7.0").cross(CrossVersion.full)
    val report = getUpdateReport(createMetaDep(excluded), scala2122)
    testScalahostIsMissing(report)
  }

  test("it should exclude any version of scala-library via * artifact id") {
    val toExclude = ExclusionRule("org.scala-lang", "*")
    val report = getUpdateReport(createLiftDep(toExclude), scala210)
    testScalaLibraryIsMissing(report)
  }

  test("it should exclude any version of scala-library via * org id") {
    val toExclude = ExclusionRule("*", "scala-library")
    val report = getUpdateReport(createLiftDep(toExclude), scala210)
    testScalaLibraryIsMissing(report)
  }

  def createLiftDep(toExclude: ExclusionRule): ModuleID =
    ("net.liftweb" %% "lift-mapper" % "2.6-M4" % "compile").excludeAll(toExclude)

  def createMetaDep(toExclude: ExclusionRule): ModuleID =
    ("org.scalameta" %% "paradise" % "3.0.0-M8" % "compile")
      .cross(CrossVersion.full)
      .excludeAll(toExclude)

  def getUpdateReport(dep: ModuleID, scalaVersion: Option[String]): UpdateReport = {
    val m = module(defaultModuleId, Vector(dep), scalaVersion)
    coursierUpdate(m)
  }

  def testLiftJsonIsMissing(report: UpdateReport): Unit = {
    assert(
      !report.allModules.exists(_.name.contains("lift-json")),
      "lift-json has not been excluded."
    )
    assert(
      !report.allModuleReports.exists(_.module.name.contains("lift-json")),
      "lift-json has not been excluded."
    )
  }

  def testScalaLibraryIsMissing(report: UpdateReport): Unit = {
    assert(
      !report.allModules.exists(_.name.contains("scala-library")),
      "scala-library has not been excluded."
    )
    assert(
      !report.allModuleReports.exists(_.module.name.contains("scala-library")),
      "scala-library has not been excluded."
    )
  }

  def testScalahostIsMissing(report: UpdateReport): Unit = {
    assert(
      !report.allModules.exists(_.name.contains("scalahost")),
      "scalahost has not been excluded."
    )
    assert(
      !report.allModuleReports.exists(_.module.name.contains("scalahost")),
      "scalahost has not been excluded."
    )
  }
}
