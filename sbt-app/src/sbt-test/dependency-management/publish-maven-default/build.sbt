// Regression guard related to sbt/sbt#535.
//
// Patterns.isMavenCompatible now defaults to false (so a custom Ivy pattern keeps [organisation]
// literal). Resolver.file still uses Resolver.mavenStylePatterns, which is explicitly Maven-compatible,
// so publishing to it must keep producing the standard slash-separated Maven layout.

ThisBuild / scalaVersion := "2.12.21"

lazy val lib = project
  .settings(
    organization := "org.example",
    name := "lib",
    version := "1.0",
    publishMavenStyle := true,
    publishTo := Some(Resolver.file("dist", (ThisBuild / baseDirectory).value / "repo"))
  )
