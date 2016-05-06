lazy val project = (sbt.syntax.project in file(".")).
  settings(
    name := "project",
    scalaVersion := "2.11.7",
    crossPaths := false
  )
