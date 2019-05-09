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
  .custom(
    idSuffix = "Config1_2",
    directorySuffix = "-config1.2",
    scalaVersions = Seq("2.12.8", "2.11.12"),
    _.settings(
      moduleName := name.value + "_config1.2",
      libraryDependencies += "com.typesafe" % "config" % "1.2.1"
    )
  )
  .custom(
    idSuffix = "Config1_3",
    directorySuffix = "-config1.3",
    scalaVersions = Seq("2.12.8", "2.11.12"),
    _.settings(
      moduleName := name.value + "_config1.3",
      libraryDependencies += "com.typesafe" % "config" % "1.3.3"
    )
  )
