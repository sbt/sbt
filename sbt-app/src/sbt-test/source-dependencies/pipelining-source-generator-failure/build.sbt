ThisBuild / scalaVersion := "2.13.12"
ThisBuild / usePipelining := true

lazy val a = project
  .settings(
    Compile / sourceGenerators += Def.task[Seq[File]] {
      sys.error("source generator failed")
    },
  )

lazy val b = project.dependsOn(a)
