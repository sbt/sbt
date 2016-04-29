lazy val root = (project in file(".")).
  settings(
    scalaVersion := "2.10.6",
    libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % Test,
    parallelExecution in test := false
  )
