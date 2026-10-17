ThisBuild / scalaVersion := "3.9.0"
ThisBuild / usePipelining := true

lazy val a = project
  .settings(
    Compile / sourceGenerators += Def.task[Seq[File]] {
      sys.error("source generator failed")
    },
  )

lazy val b = project.dependsOn(a)
