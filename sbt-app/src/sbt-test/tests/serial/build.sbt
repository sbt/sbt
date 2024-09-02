val scalatest = "org.scalatest" %% "scalatest" % "3.0.5"

ThisBuild / scalaVersion := "2.12.20"
ThisBuild / organization := "com.example"
ThisBuild / version      := "0.0.1-SNAPSHOT"

val commonSettings = Seq(
  libraryDependencies += scalatest % Test
)

lazy val root = (project in file("."))
  .aggregate(sub1, sub2)
  .settings(
    commonSettings
  )

lazy val rootRef = LocalProject("root")

lazy val sub1 = project
  .dependsOn(rootRef)
  .settings(
    commonSettings
  )

lazy val sub2 = project
  .dependsOn(rootRef)
  .settings(
    commonSettings
  )
