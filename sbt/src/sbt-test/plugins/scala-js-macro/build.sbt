ThisBuild / scalaVersion := "2.13.1"

lazy val root = (project in file("."))
  .aggregate(macroProvider, macroClient)

lazy val macroProvider = (project in file("macro-provider"))
  .settings(
    libraryDependencies += scalaVersion("org.scala-lang" % "scala-reflect" % _ ).value
  )

lazy val macroClient = (project in file("macro-client"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(macroProvider)
