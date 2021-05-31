val scala3Version = "3.0.1-RC1-bin-20210525-8f3fdf5-NIGHTLY"

enablePlugins(ScalaJSPlugin)

lazy val root = project
  .in(file("."))
  .settings(
    name := "scala3-simple",
    version := "0.1.0",

    scalaVersion := scala3Version,
  )
