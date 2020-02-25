ThisBuild / scalaVersion     := "2.13.1"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "sbt-5308",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.0" % Test,
    libraryDependencies += "com.vladsch.flexmark" % "flexmark-all" % "0.35.10" % Test,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/generated/test-reports"),
  )
