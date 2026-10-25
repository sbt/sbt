ThisBuild / scalaVersion := "2.13.12"
ThisBuild / usePipelining := true

lazy val a = project
  .settings(
    Compile / compileAnalysisFilename := {
      sys.error("analysis filename failed"): String
    },
  )

lazy val b = project.dependsOn(a)
