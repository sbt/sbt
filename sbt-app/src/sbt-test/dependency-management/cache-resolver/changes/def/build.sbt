ThisBuild / organization := "org.example"
ThisBuild / version := "2.0-SNAPSHOT"

lazy val root = (project in file(".")).
  aggregate(a,b).
  settings(
    ivyPaths := (ThisBuild / ivyPaths).value,
  )

lazy val a = project.
  dependsOn(b).
  settings(
    name := "a",
    ivyPaths := (ThisBuild / ivyPaths).value,
  )

lazy val b = project.
  settings(
    name := "b",
    ivyPaths := (ThisBuild / ivyPaths).value,
  )
