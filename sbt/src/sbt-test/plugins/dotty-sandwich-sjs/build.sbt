// TODO use 2.13.4 when it's out
ThisBuild / scalaVersion := "2.13.4-bin-d526da6"

Global / resolvers += "scala-integration".at("https://scala-ci.typesafe.com/artifactory/scala-integration/")

lazy val scala3code = project
  .enablePlugins(ScalaJSPlugin)
  .settings(scalaVersion := "0.27.0-RC1")

lazy val app = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(scala3code)
  .settings(
    libraryDependencies ~= (_.filterNot(_.name.contains("scalajs-compiler"))),
    addCompilerPlugin("org.scala-js" % "scalajs-compiler_2.13.3" % scalaJSVersion),
    scalaJSUseMainModuleInitializer := true,
  )
