organization := "org.example"

name := "demo-compiler-plugin"

version := "0.1"

scalaVersion := "2.12.20"
libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"

exportJars := true