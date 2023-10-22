ThisBuild / scalaVersion := "2.12.17"
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

val commonSettings = Seq(
  organization := "com.example",
  version := "0.1.0",
  ivyPaths := IvyPaths((LocalRootProject / baseDirectory).value, Some((LocalRootProject / target).value / "ivy-cache"))
)

lazy val app = (project in file("app")).
  settings(commonSettings: _*)

name := "generated-root-no-publish"
commonSettings
