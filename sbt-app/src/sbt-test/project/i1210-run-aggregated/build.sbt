import Commands.*

ThisBuild / autoScalaLibrary := false

// Build-scoped: Project.updateCurrent registers only the current project's, the
// build's and Global's commands, and this test navigates to projA.
ThisBuild / commands ++= Seq(runAgg, runAggScoped, probeWarn, runAggOrphan, runTaskUndefined)

lazy val root = (project in file("."))
  .aggregate(projA, projB)
  .settings(
    markTask := Def.uncached(IO.write(baseDirectory.value / "marker.txt", "root"))
  )

lazy val projA = project
  .settings(
    markTask := Def.uncached(IO.write(baseDirectory.value / "marker.txt", "projA"))
  )

lazy val projB = project
  .settings(
    markTask := Def.uncached(IO.write(baseDirectory.value / "marker.txt", "projB"))
  )
