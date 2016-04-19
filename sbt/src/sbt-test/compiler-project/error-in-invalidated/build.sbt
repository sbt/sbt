lazy val root = (project in file(".")).
  settings(
    incOptions := sbt.inc.IncOptions.Default,
    scalaVersion := "2.11.7"
  )
