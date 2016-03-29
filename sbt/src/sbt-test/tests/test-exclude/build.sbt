lazy val root = (project in file(".")).
  settings(
    libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % Test,
    parallelExecution in test := false
  )
