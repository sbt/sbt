ThisBuild / scalaVersion := "2.13.1"

Global / serverLog / logLevel := Level.Debug

lazy val root = (project in file("."))
  .aggregate(foo, util)

lazy val foo = project.in(file("foo"))
  .settings(
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  )
  .dependsOn(util)

lazy val util = project
