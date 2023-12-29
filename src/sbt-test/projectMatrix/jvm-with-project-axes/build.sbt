import sbt.internal.ProjectMatrix
ThisBuild / organization := "com.example"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / publishMavenStyle := true

ThisBuild / ivyPaths := {
  val base = (ThisBuild / baseDirectory).value
  IvyPaths(base, Some(base / "ivy-cache"))
}
publish / skip := true

lazy val scala212 = "2.12.10"

lazy val check = taskKey[Unit]("")

lazy val config12 = ConfigAxis("Config1_2", "-config1.2")
lazy val config13 = ConfigAxis("Config1_3", "-config1.3")

check := {
  val app12 = app.finder(config12).apply(autoScalaLibrary = false)
  assert(app12.id == "appConfig1_2", s"app12.id is ${app12.id}")
  val app13 = app.finder(config13).apply(autoScalaLibrary = false)
  assert(app13.id == "appConfig1_3", s"app13.id is ${app13.id}")
}

lazy val app: ProjectMatrix = (projectMatrix in file("app"))
  .settings(
    name := "app",
    ivyPaths := (ThisBuild / ivyPaths).value
  )
  .customRow(
    autoScalaLibrary = false,
    scalaVersions = Seq(scala212),
    axisValues = Seq(config12, VirtualAxis.jvm),
    _.settings(
      libraryDependencies += "com.typesafe" % "config" % "1.2.1"
    )
  )
  .customRow(
    autoScalaLibrary = false,
    scalaVersions = Seq(scala212),
    axisValues = Seq(config13, VirtualAxis.jvm),
    _.settings(
      libraryDependencies += "com.typesafe" % "config" % "1.3.3"
    )
  )
