ThisBuild / scalaVersion := "2.13.4"
ThisBuild / scalacOptions += "-Ytasty-reader"

lazy val scala3code = project
  .enablePlugins(ScalaJSPlugin)
  .settings(scalaVersion := "3.0.0-M1")

lazy val app = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(scala3code)
  .settings(
    libraryDependencies ~= (_.filterNot(_.name.contains("scalajs-compiler"))),
    addCompilerPlugin("org.scala-js" % "scalajs-compiler_2.13.4" % scalaJSVersion),
    scalaJSUseMainModuleInitializer := true,
  )
