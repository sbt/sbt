ThisBuild / turbo := true

val akkaTest = (project in file(".")).settings(
  name := "akka-test",
  scalaVersion := "2.12.20",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.5.16",
    "com.lihaoyi" %% "utest" % "0.6.6" % "test"
  ),
  testFrameworks := Seq(new TestFramework("utest.runner.Framework"))
)
