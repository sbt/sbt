lazy val verify = "com.eed3si9n.verify" %% "verify" % "1.0.0"

Global / localCacheDirectory := baseDirectory.value / "diskcache"

scalaVersion := "3.8.4"
libraryDependencies += verify % Test
testFrameworks += new TestFramework("verify.runner.Framework")

