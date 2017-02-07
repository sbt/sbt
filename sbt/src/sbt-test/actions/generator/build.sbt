lazy val buildInfo = taskKey[Seq[File]]("The task that generates the build info.")

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.11.8",
    buildInfo := {
      val x = sourceManaged.value / "BuildInfo.scala"
      IO.write(x, """object BuildInfo""")
      x :: Nil
    },
    sourceGenerators in Compile += buildInfo,
    sourceGenerators in Compile += Def.task { Nil }
  )
