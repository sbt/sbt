import Commands.*

ThisBuild / autoScalaLibrary := false

lazy val root = (project in file("."))
  .aggregate(projA, projB)
  .settings(
    commands ++= Seq(runAgg, runAggScoped, probeWarn, runAggOrphan, runTaskUndefined)
  )

lazy val projA = project
  .settings(
    markTask := Def.uncached(IO.write(baseDirectory.value / "marker.txt", "projA"))
  )

lazy val projB = project
  .settings(
    markTask := Def.uncached(IO.write(baseDirectory.value / "marker.txt", "projB"))
  )
