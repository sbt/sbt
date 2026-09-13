val scalatest = "org.scalatest" %% "scalatest" % "3.2.19"

ThisBuild / scalaVersion := "3.9.0"

lazy val root = (project in file("."))

lazy val sub = project
  .settings(
    Test / fork := true,
    libraryDependencies += scalatest % Test,
  )
