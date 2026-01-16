ThisBuild / scalaVersion := "2.12.21"
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

organization := "com.example"
version := "0.1.0"
ivyPaths := IvyPaths(
  (LocalRootProject / baseDirectory).value.toString,
  Some(((LocalRootProject / target).value / "ivy-cache").toString)
)
