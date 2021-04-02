lazy val project = (sbt.project in file(".")).
  settings(
    name := "project",
    scalaVersion := "2.11.7",
    crossPaths := false
  )
