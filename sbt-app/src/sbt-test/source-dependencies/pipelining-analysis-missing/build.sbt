ThisBuild / scalaVersion := "3.9.0"
ThisBuild / usePipelining := true

Global / localCacheDirectory := baseDirectory.value / "diskcache"

lazy val deleteAnalysis = taskKey[Unit]("Deletes the analysis file of a")

lazy val a = project
  .settings(
    Compile / compileIncSetup := {
      val setup = (Compile / compileIncSetup).value
      val declared = (Compile / compileAnalysisTargetRoot).value / "declared-analysis.zip"
      if !declared.exists then IO.write(declared, "declared")
      setup.withCacheFile(declared)
    },
    deleteAnalysis := Def.uncached(IO.delete((Compile / compileAnalysisFile).value)),
  )

lazy val b = project.dependsOn(a)
