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

// check that the buildTarget/compile request fails with the custom message defined below
lazy val respondError = project.in(file("respond-error"))
  .settings(
    Compile / compile := {
      val _ = (Compile / compile).value
      throw new MessageOnlyException("custom message")
    }
  )

lazy val util = project
