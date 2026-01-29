ThisBuild / scalaVersion := "2.12.21"
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

val commonSettings = Seq(
  organization := "com.example",
  version := "0.1.0",
  ivyPaths := IvyPaths(
    (LocalRootProject / baseDirectory).value.toString,
    Some(((LocalRootProject / baseDirectory).value / "ivy-cache").toString)
  )
)

lazy val app = (project in file("app")).settings(commonSettings*)

LocalRootProject / name := "generated-root-no-publish"
commonSettings
