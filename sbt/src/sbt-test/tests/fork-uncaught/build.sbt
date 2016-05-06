scalaVersion := "2.11.8"

organization := "org.example"

name := "fork-uncaught"

fork := true

libraryDependencies ++= List(
  "org.scala-lang.modules" %% "scala-xml" % "1.0.1",
  "org.scalatest" %% "scalatest" % "2.2.6" % Test intransitive()
)
