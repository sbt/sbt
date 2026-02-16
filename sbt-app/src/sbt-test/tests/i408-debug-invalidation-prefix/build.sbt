// #408: debug invalidation output should indicate project
// Multi-project build so we get multiple compile debug blocks
scalaVersion := "2.12.21"

lazy val root = (project in file("."))
  .aggregate(a, b)
  .settings(name := "root")

lazy val a = (project in file("a"))
  .settings(name := "a")

lazy val b = (project in file("b"))
  .settings(name := "b")
  .dependsOn(a)
