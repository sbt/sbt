organization := "com.example"
version := "0.1.0-SNAPSHOT"
publishMavenStyle := true
ivyPaths := {
  val base = baseDirectory.value
  val thisBuildBase = (ThisBuild / baseDirectory).value
  IvyPaths(base.toString, s"$thisBuildBase/ivy-cache")
}

lazy val config12 = ConfigAxis("Config1_2", "config1.2")
lazy val config13 = ConfigAxis("Config1_3", "config1.3")

lazy val scala212 = "2.12.10"
lazy val scala211 = "2.11.12"

lazy val core = (projectMatrix in file("core"))
  .jvmPlatform(scalaVersions = Seq(scala212, scala211))

lazy val app = (projectMatrix in file("app"))
  .dependsOn(core)
  .settings(
    name := "app",
  )
  .customRow(
    scalaVersions = Seq(scala212, scala211),
    axisValues = Seq(config12, VirtualAxis.jvm),
    _.settings(
      moduleName := name.value + "_config1.2",
      libraryDependencies += "com.typesafe" % "config" % "1.2.1"
    )
  )
  .customRow(
    scalaVersions = Seq(scala212, scala211),
    axisValues = Seq(config13, VirtualAxis.jvm),
    _.settings(
      moduleName := name.value + "_config1.3",
      libraryDependencies += "com.typesafe" % "config" % "1.3.3"
    )
  )

lazy val appConfig12_212 = app.finder(config13, VirtualAxis.jvm)(scala212)
  .settings(
    publishMavenStyle := true
  )
