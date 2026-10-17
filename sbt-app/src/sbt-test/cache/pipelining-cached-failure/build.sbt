ThisBuild / scalaVersion := "2.13.16"
ThisBuild / usePipelining := true

Global / localCacheDirectory := baseDirectory.value / "diskcache"

lazy val a = project
lazy val b = project.dependsOn(a)
lazy val c = project.dependsOn(b)
