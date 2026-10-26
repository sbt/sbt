ThisBuild / scalaVersion := "2.13.12"
ThisBuild / usePipelining := true

lazy val a = project
  .settings(
    Compile / compile / bspReporter := {
      sys.error("bsp reporter failed"): sbt.internal.server.BuildServerReporter
    },
  )

lazy val b = project.dependsOn(a)
