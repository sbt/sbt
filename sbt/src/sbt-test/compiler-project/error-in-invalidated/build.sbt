ThisBuild / scalaVersion := "2.12.9"

lazy val root = (project in file(".")).
  settings(
    incOptions := xsbti.compile.IncOptions.of()
  )
