ThisBuild / turbo := true

val utestTest = (project in file(".")).settings(
  name := "utest-test",
  scalaVersion := "2.12.20",
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "utest" % "0.6.6" % "test"
  ),
  testFrameworks += TestFramework("utest.runner.Framework")
)
