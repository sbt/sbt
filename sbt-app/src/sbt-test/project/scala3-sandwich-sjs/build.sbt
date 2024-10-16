ThisBuild / scalaVersion := "2.13.15"
ThisBuild / scalacOptions += "-Ytasty-reader"

lazy val scala3code = project
  .enablePlugins(ScalaJSPlugin)
  .settings(scalaVersion := "3.3.4")

lazy val app = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(scala3code)
  .settings(
    scalaVersion := "2.13.15",
    scalacOptions += "-Ytasty-reader",
    scalaJSUseMainModuleInitializer := true
  )
