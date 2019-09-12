lazy val scala212 = "2.12.10"
// keep this at M5 to test full version
lazy val scala213 = "2.13.0-M5"

ThisBuild / crossScalaVersions := Seq(scala212, scala213)
ThisBuild / scalaVersion       := scala212

lazy val rootProj = (project in file("."))
  .aggregate(libProj, fooPlugin)
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
    crossScalaVersions := Seq(scala212)
  )

lazy val extrasProj = (project in file("extras"))
  .settings(
    name := "foo-extras",
  )
