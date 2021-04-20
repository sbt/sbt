ThisBuild / scalaVersion := "2.13.3"
ThisBuild / usePipelining := true

lazy val root = (project in file("."))
  .aggregate(dep, use)
  .settings(
    name := "pipelining Java",
  )

lazy val dep = project

lazy val use = project
  .dependsOn(dep)
