val scalcheck = "org.scalacheck" %% "scalacheck" % "1.14.0"

ThisBuild / scalaVersion := "2.12.18"
Test / parallelExecution := false
libraryDependencies += scalcheck % Test
