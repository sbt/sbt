scalaVersion in ThisBuild := "2.11.8"
concurrentRestrictions in Global := Seq(Tags.limitAll(4))
libraryDependencies += "org.specs2" %% "specs2-core" % "3.8.4" % Test
inConfig(Test)(Seq(
  testGrouping := definedTests.value.map { test => new Tests.Group(test.name, Seq(test), Tests.SubProcess(
    ForkOptions(
      javaHome.value,
      outputStrategy.value,
      Nil,
      Some(baseDirectory.value),
      javaOptions.value,
      connectInput.value,
      envVars.value
    )
  ))},
  TaskKey[Unit]("test-failure") := test.failure.value
))
