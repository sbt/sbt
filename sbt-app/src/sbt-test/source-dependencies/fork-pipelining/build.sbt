ThisBuild / scalaVersion := "3.7.4"
ThisBuild / usePipelining := true
ThisBuild / forkCompile := true

lazy val a = project

lazy val b = project.dependsOn(a)
