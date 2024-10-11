ThisBuild / scalaVersion := "2.12.20"

lazy val root = (project in file("."))
  .settings(
    libraryDependencies += "com.lihaoyi" %% "utest" % "0.6.4" % Test,
    testFrameworks += new TestFramework("utest.runner.Framework"),
    Test / fork := true
  )
