lazy val scala3 = "3.3.1"
lazy val scala213 = "2.13.1"

crossScalaVersions := Seq(scala3, scala213)
ThisBuild / scalaVersion := scala3

lazy val rootProj = (project in file("."))
  .aggregate(libProj, fooPlugin, externalProj)
  .settings(
    crossScalaVersions := Nil,
    addCommandAlias("build", "compile")
  )

lazy val libProj = (project in file("lib"))
  .settings(
    name := "foo-lib"
  )

lazy val fooPlugin = (project in file("sbt-foo"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-foo",
    crossScalaVersions := Seq(scala3),
  )

lazy val extrasProj = (project in file("extras"))
  .settings(
    name := "foo-extras",
  )

lazy val externalProj = ProjectRef(file("ref"), "external")
