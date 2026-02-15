ThisBuild / scalaVersion := "2.12.21"

lazy val root = (project in file("."))
  .settings(
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % Test,
    Test / fork := false,
    Test / parallelExecution := false
  )
