lazy val scala212 = "2.12.10"
lazy val scala213 = "2.13.1"

ThisBuild / scalaVersion := scala212

lazy val root = (project in file("."))
  .aggregate(core, module)
  .settings(
    crossScalaVersions := Nil
  )

lazy val core = (project in file("core"))
  .settings(
    crossScalaVersions := Seq(scala212, scala213))

lazy val module = (project in file("module"))
  .dependsOn(core)
  .settings(
    crossScalaVersions := Seq(scala212)
  )
