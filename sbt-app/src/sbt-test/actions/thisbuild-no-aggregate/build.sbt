ThisBuild / scalaVersion := "2.13.16"

import Marker.autoImport._

// Assign mark task at ThisBuild scope using the build-level baseDirectory.
// With the fix, this should only run once at build level, not aggregate into sub-projects.
ThisBuild / mark := {
  val base = (ThisBuild / baseDirectory).value
  val toMark = base / "ran"
  if (toMark.exists) sys.error(s"Already ran ($toMark exists)")
  else IO.touch(toMark)
}

// Root aggregates sub. Verifies that ThisBuild-scoped keys do NOT aggregate
// (fix for sbt/sbt#5349, PR #8703).
lazy val root = (project in file("."))
  .aggregate(sub)
  .settings(name := "root")

lazy val sub = (project in file("sub"))
  .settings(name := "sub")
