
enablePlugins(coursier.ShadingPlugin)
shadingNamespace := "test.shaded"

libraryDependencies ++= Seq(
  "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M4" % "shaded",
  "com.chuusai" %% "shapeless" % "2.3.2",
  "org.scala-lang" % "scala-reflect" % scalaVersion.value
)

scalaVersion := "2.11.8"
organization := "io.get-coursier.test"
name := "shading-transitive-test"
version := "0.1.0-SNAPSHOT"
