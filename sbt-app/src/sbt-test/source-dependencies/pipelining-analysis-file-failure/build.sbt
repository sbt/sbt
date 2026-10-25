ThisBuild / scalaVersion := "3.9.0"
ThisBuild / usePipelining := true

lazy val a = project
  .settings(
    Compile / compileAnalysisFilename := Def.uncached {
      sys.error("analysis filename failed"): String
    },
  )

lazy val b = project.dependsOn(a)
