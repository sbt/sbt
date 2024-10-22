val scalcheck = "org.scalacheck" %% "scalacheck" % "1.14.0"

ThisBuild / scalaVersion := "2.12.20"

lazy val root = (project in file("."))
  .settings(
    name := "forked-test",
    organization := "org.example",
    libraryDependencies += scalcheck % Test
  )
