lazy val scala212 = "2.12.21"
lazy val scala213 = "2.13.12"

ThisBuild / scalaVersion := scala212

lazy val root = (project in file("."))
  .settings(
    crossScalaVersions := Seq(scala212, scala213),
  )
