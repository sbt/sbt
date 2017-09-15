inThisBuild(List(
  crossScalaVersions := Seq("2.12.1", "2.11.8")
))

lazy val rootProj = (project in file("."))
  .aggregate(libProj, fooPlugin)
  .settings(
    scalaVersion := "2.12.1"
  )

lazy val libProj = (project in file("lib"))
  .settings(
    name := "foo-lib",
    scalaVersion := "2.12.1",
    crossScalaVersions := Seq("2.12.1", "2.11.8")
  )

lazy val fooPlugin = (project in file("sbt-foo"))
  .settings(
    name := "sbt-foo",
    sbtPlugin := true,
    scalaVersion := "2.12.1",
    crossScalaVersions := Seq("2.12.1")
  )

lazy val extrasProj = (project in file("extras"))
  .settings(
    name := "foo-extras",
  )

addCommandAlias("build", "compile")
