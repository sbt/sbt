// #408: incremental compile debug output should indicate project in multi-project builds.
// project/build.properties uses sbt 2.0.0-RC9-bin-SNAPSHOT so scripted runs with the built sbt,
// where CompileDebugLogger prefixes debug/info lines with [projectId] (e.g. [a], [b]).
scalaVersion := "2.12.21"
resolvers += Resolver.mavenLocal

lazy val root = (project in file("."))
  .aggregate(a, b)
  .settings(name := "root")

lazy val a = (project in file("a"))
  .settings(name := "a")

lazy val b = (project in file("b"))
  .dependsOn(a)
  .settings(name := "b")
