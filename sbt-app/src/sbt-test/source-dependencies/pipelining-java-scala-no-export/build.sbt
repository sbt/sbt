ThisBuild / scalaVersion := "2.13.18"
ThisBuild / usePipelining := true

lazy val root = (project in file("."))
  .aggregate(upstream, downstream)

lazy val upstream = project
  .settings(
    exportJars := true,
    exportPipelining := false,
  )

lazy val downstream = project
  .dependsOn(upstream)
