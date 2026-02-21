ThisBuild / scalaVersion := "2.12.21"

Test / autoScalaLibrary := false
libraryDependencies += "org.scala-lang" % "scala-library" % scalaVersion.value % Test
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
