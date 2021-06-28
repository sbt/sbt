ThisBuild / scalaVersion := "3.0.0"

lazy val scala213 = "2.13.6"

lazy val root = (project in file("."))
  .aggregate(fooApp, fooCore, barApp, barCore)

lazy val fooApp = (project in file("foo-app"))
  .dependsOn(fooCore)

lazy val fooCore = (project in file("foo-core"))
  .settings(
    scalaVersion := scala213,
  )

lazy val barApp = (project in file("bar-app"))
  .dependsOn(barCore)
  .settings(
    scalaVersion := scala213,
    scalacOptions += "-Ytasty-reader"
  )

lazy val barCore = (project in file("bar-core"))
