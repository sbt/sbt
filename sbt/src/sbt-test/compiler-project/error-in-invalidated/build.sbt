ThisBuild / scalaVersion := "2.12.8"

lazy val root = (project in file(".")).
  settings(
    incOptions := xsbti.compile.IncOptions.of()
  )
