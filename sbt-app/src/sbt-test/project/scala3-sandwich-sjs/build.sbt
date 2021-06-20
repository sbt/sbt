ThisBuild / scalaVersion := "2.13.6"
ThisBuild / scalacOptions += "-Ytasty-reader"

lazy val scala3code = project
  .enablePlugins(ScalaJSPlugin)
  .settings(scalaVersion := "3.0.0")

lazy val app = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(scala3code)
  .settings(
    scalaVersion := "2.13.6",
    scalacOptions += "-Ytasty-reader",
    scalaJSUseMainModuleInitializer := true
  )
