ThisBuild / scalaVersion := "2.13.1"

Global / serverLog / logLevel := Level.Debug

lazy val root = (project in file("."))
  .aggregate(foo, util)

lazy val foo = project.in(file("foo"))
  .dependsOn(util)

lazy val util = project
