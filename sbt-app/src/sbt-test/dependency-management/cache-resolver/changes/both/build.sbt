ThisBuild / organization := "org.example"
ThisBuild / version := "2.0-SNAPSHOT"

lazy val root = (project in file(".")).
  aggregate(a,b).
  settings(
    name := "use",
    version := "1.0",
    libraryDependencies += "org.example" % "b" % "2.0-SNAPSHOT",
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
