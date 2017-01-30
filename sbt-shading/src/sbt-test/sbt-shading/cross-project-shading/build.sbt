
lazy val root = crossProject
  .in(file("."))
  .jvmConfigure(
    _.enablePlugins(coursier.ShadingPlugin)
  )
  .jvmSettings(
    shadingNamespace := "test.shaded",
    libraryDependencies += "io.argonaut" %% "argonaut" % "6.2-RC2" % "shaded"
  )
  .settings(
    scalaVersion := "2.11.8",
    organization := "io.get-coursier.test",
    name := "shading-cross-test",
    version := "0.1.0-SNAPSHOT",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )

lazy val jvm = root.jvm
lazy val js = root.js
