ThisBuild / semanticdbEnabled := true

Global / localCacheDirectory := baseDirectory.value / "diskcache"

lazy val targetroot2 = project
  .settings(scalaVersion := "2.13.18")

lazy val injar2 = project
  .settings(scalaVersion := "2.13.18", semanticdbIncludeInJar := true)

lazy val targetroot3 = project
  .settings(scalaVersion := "3.9.0")

lazy val injar3 = project
  .settings(scalaVersion := "3.9.0", semanticdbIncludeInJar := true)
