
enablePlugins(coursier.ShadingPlugin)
shadingNamespace := "test.shaded"

libraryDependencies ++= Seq(
  "io.argonaut" %% "argonaut" % "6.2-RC2" % "shaded",
  "org.scala-lang" % "scala-reflect" % scalaVersion.value // not shading that one
)

scalaVersion := "2.11.8"
organization := "io.get-coursier.test"
name := "shading-base-test"
version := "0.1.0-SNAPSHOT"
