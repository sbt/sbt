val scalatest = "org.scalatest" %% "scalatest" % "3.0.5"

ThisBuild / scalaVersion := "2.12.9"

fork := true
libraryDependencies += scalatest % Test
