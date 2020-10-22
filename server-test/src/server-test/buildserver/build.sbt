ThisBuild / scalaVersion := "2.13.1"

Global / serverLog / logLevel := Level.Debug

lazy val runAndTest = project.in(file("run-and-test"))
  .settings(
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  )
  .dependsOn(util)

lazy val reportError = project.in(file("report-error"))

lazy val reportWarning = project.in(file("report-warning"))
  .settings(
    scalacOptions += "-deprecation"
  )

lazy val util = project
