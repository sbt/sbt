ThisBuild / scalaVersion := "2.13.1"

lazy val root = (project in file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    libraryDependencies += "org.scala-js" %%% "scalajs-java-time" % "1.0.0"
  )
