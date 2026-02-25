ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(
    name := "update-report-cache-persistence-test",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.18" % Test
  )
