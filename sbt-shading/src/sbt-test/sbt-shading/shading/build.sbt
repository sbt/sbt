
enablePlugins(coursier.ShadingPlugin)
shadingNamespace := "test.shaded"

libraryDependencies += "io.argonaut" %% "argonaut" % "6.2-RC2" % "shaded"

scalaVersion := "2.11.8"
organization := "io.get-coursier.test"
name := "shading-base-test"
version := "0.1.0-SNAPSHOT"
