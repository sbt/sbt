val scalatest = "org.scalatest" %% "scalatest" % "3.2.19"

ThisBuild / scalaVersion := "3.8.4"

lazy val root = (project in file("."))

lazy val sub = project
  .settings(
    Test / fork := true,
    libraryDependencies += scalatest % Test,
  )
