ThisBuild / scalaVersion := "3.9.0"
ThisBuild / usePipelining := true

lazy val core = project

lazy val app = project.dependsOn(core)
