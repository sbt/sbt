val scalatest = "org.scalatest" %% "scalatest" % "3.0.5"

lazy val root = (project in file("."))
  .settings(
    // Verifies that a different scala library version still works in test
    scalaVersion := "2.11.12",
    libraryDependencies += scalatest % Test
  )
