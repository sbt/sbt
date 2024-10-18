val scala3Version = "3.3.4"

enablePlugins(ScalaJSPlugin)

lazy val root = project
  .in(file("."))
  .settings(
    name := "scala3-simple",
    version := "0.1.0",

    scalaVersion := scala3Version,
  )
