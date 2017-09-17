lazy val runClient = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.12.3",
    serverPort in Global := 5123,
    libraryDependencies += "org.scala-sbt" %% "io" % "1.0.1",
    libraryDependencies += "com.eed3si9n" %%  "sjson-new-scalajson" % "0.8.0",
    runClient := (Def.taskDyn {
      val b = baseDirectory.value
      (bgRun in Compile).toTask(s""" $b""")
    }).value
  )
 