lazy val root = (project in file(".")).
  settings(
    scalaVersion := "2.11.8",
    libraryDependencies ++= List(
      "org.scala-lang.modules" %% "scala-xml" % "1.0.1",
      "org.scalatest" %% "scalatest" % "2.2.6"
    ),
    parallelExecution in test := false
  )
