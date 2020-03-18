ThisBuild / scalaVersion := "2.12.11"

lazy val root = (project in file(".")).
  settings(
    incOptions := xsbti.compile.IncOptions.of()
  )
