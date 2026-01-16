val scalacheck = "org.scalacheck" %% "scalacheck" % "1.14.0"

ThisBuild / scalaVersion := "2.12.21"
Test / parallelExecution := false
libraryDependencies += scalacheck % Test
