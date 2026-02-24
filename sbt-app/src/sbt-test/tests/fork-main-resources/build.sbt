val scalatest = "org.scalatest" %% "scalatest" % "3.2.19"

scalaVersion := "3.8.2"
Test / fork := true
libraryDependencies += scalatest % Test

