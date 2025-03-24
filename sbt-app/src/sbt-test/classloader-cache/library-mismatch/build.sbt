ThisBuild / turbo := true

val snapshot = (project in file(".")).settings(
  name := "mismatched-libraries",
  scalaVersion := "2.12.20",
  libraryDependencies ++= Seq("com.lihaoyi" %% "utest" % "0.6.6" % "test"),
  testFrameworks := Seq(TestFramework("utest.runner.Framework")),
  resolvers += "Local Maven" at file("libraries/ivy").toURI.toURL.toString,
  libraryDependencies += "sbt" % "transitive-lib" % "0.1.0",
  libraryDependencies += "sbt" % "foo-lib" % "0.2.0" % "test",
)
