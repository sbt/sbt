ThisBuild / scalaVersion := "2.13.16"

// Root aggregates sub. Verifies that ThisBuild-scoped keys do NOT aggregate
// (fix for sbt/sbt#5349, PR #8703).
lazy val root = (project in file("."))
  .aggregate(sub)
  .settings(name := "root")

lazy val sub = (project in file("sub"))
  .settings(name := "sub")
