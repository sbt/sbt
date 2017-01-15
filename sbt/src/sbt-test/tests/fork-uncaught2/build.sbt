scalaVersion := "2.11.8"

libraryDependencies += "org.scala-sbt" % "test-interface" % "1.0"

testFrameworks := new TestFramework("build.MyFramework") :: Nil

fork := true

definedTests in Test += new sbt.TestDefinition(
      "my",
      // marker fingerprint since there are no test classes
      // to be discovered by sbt:
      new sbt.testing.AnnotatedFingerprint {
        def isModule = true
        def annotationName = "my"
      }, true, Array()
    )
