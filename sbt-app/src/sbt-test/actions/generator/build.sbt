val buildInfo = taskKey[Seq[File]]("generates the build info")

ThisBuild / scalaVersion := "2.12.19"

lazy val root = (project in file("."))
  .settings(
    buildInfo := {
      val file = sourceManaged.value / "BuildInfo.scala"
      IO.write(file, "object BuildInfo")
      file :: Nil
    },
    Compile / sourceGenerators += buildInfo,
    Compile / sourceGenerators += Def.task { Seq.empty[File] },
  )
