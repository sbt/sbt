// sbt#4847: inter-project deps with variant Scala binary versions.
// bar uses 2.12, baz uses 2.13; baz dependsOn(bar). Resolution must ask for bar_2.12, not bar_2.13.
ThisBuild / organization := "com.example"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val foo = project in file(".")
aggregateProjects(bar, baz)

lazy val bar = project.settings(
  scalaVersion := "2.12.18",
  name := "bar",
)

lazy val baz = project
  .dependsOn(bar)
  .settings(
    scalaVersion := "2.13.12",
    name := "baz",
  )
