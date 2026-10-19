ThisBuild / scalaVersion := "3.9.0"

Test / autoScalaLibrary := false
libraryDependencies += "org.scala-lang" %% "scala3-library" % scalaVersion.value % Test
libraryDependencies += "com.eed3si9n.verify" %% "verify" % "1.0.0" % Test
testFrameworks += new TestFramework("verify.runner.Framework")
