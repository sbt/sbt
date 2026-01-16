organization := "org.example"

name := "demo-compiler-plugin"

version := "0.1"

scalaVersion := "2.12.21"
libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"

exportJars := true