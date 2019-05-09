ThisBuild / organization := "com.example"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / publishMavenStyle := true

ThisBuild / ivyPaths := {
  val base = (ThisBuild / baseDirectory).value
  IvyPaths(base, Some(base / "ivy-cache"))
}
publish / skip := true

lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core",
    ivyPaths := (ThisBuild / ivyPaths).value
  )
  .crossLibrary(
    scalaVersions = Seq("2.12.8", "2.11.12"),
    suffix = "Config1.2",
    settings = Seq(
      libraryDependencies += "com.typesafe" % "config" % "1.2.1"
    )
  )
  .crossLibrary(
    scalaVersions = Seq("2.12.8"),
    suffix = "Config1.3",
    settings = Seq(
      libraryDependencies += "com.typesafe" % "config" % "1.3.3"
    )
  )

// to reference project ref
lazy val coreConfig1_3 = core.crossLib("Config1.3")("2.12.8")
  .settings(
    publishMavenStyle := true
  )
