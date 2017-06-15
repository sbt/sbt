
// for SbtExclusionRule with sbt 1.0
import Compatibility._

enablePlugins(coursier.ShadingPlugin)
shadingNamespace := "test.shaded"
shadeNamespaces += "argonaut"

libraryDependencies ++= Seq(
  "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M5" % "shaded",
  // directly depending on that one for it not to be shaded
  "org.scala-lang" % "scala-reflect" % scalaVersion.value
)

excludeDependencies += SbtExclusionRule("com.chuusai", "shapeless_2.11")

scalaVersion := "2.11.8"
organization := "io.get-coursier.test"
name := "shading-exclude-dependencies"
version := "0.1.0-SNAPSHOT"
