import sbt.pipeliningcompilefailure.Check

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / usePipelining := true

lazy val a = project
lazy val b1 = project.dependsOn(a)
lazy val b2 = project.dependsOn(a)
lazy val c = project.dependsOn(b1, b2)

lazy val root = (project in file("."))
  .aggregate(a, b1, b2, c)
  .settings(commands += Check.checkCompileFailure)
