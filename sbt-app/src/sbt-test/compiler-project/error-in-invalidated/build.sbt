ThisBuild / scalaVersion := "2.12.20"

lazy val root = (project in file(".")).
  settings(
    incOptions := xsbti.compile.IncOptions.of()
  )
