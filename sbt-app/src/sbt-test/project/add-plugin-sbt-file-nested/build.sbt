ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .aggregate(c1)

lazy val c1 = project
