ThisBuild / scalaVersion := "3.0.0-M1"
ThisBuild / scalacOptions += "-Ytasty-reader"

lazy val scala213 = "2.13.4"

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
