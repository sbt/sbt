scalaVersion := "3.8.1"
libraryDependencies += "com.eed3si9n.verify" %% "verify" % "1.0.0" % Test
testFrameworks += new TestFramework("verify.runner.Framework")
