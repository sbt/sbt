ThisBuild / scalaVersion := "0.27.0-RC1"

ThisBuild / resolvers += "scala-integration" at "https://scala-ci.typesafe.com/artifactory/scala-integration/"
// TODO use 2.13.4 when it's out
lazy val scala213 = "2.13.4-bin-d526da6"

lazy val root = (project in file("."))
  .aggregate(fooApp, fooCore, barApp, barCore)

lazy val fooApp = (project in file("foo-app"))
  .dependsOn(fooCore)

lazy val fooCore = (project in file("foo-core"))
  .settings(
    scalaVersion := scala213
  )

lazy val barApp = (project in file("bar-app"))
  .dependsOn(barCore)
  .settings(
    scalaVersion := scala213
  )

lazy val barCore = (project in file("bar-core"))
