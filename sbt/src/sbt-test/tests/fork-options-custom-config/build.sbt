// test setup
lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"
lazy val SlowTests = config("slowtests").extend(Test)

configs(SlowTests)
inConfig(SlowTests)(Defaults.testSettings)
name := "fork-options-custom-config-test"

libraryDependencies += scalaTest % Test

// fork processes so the forkOptions kick in
fork := true

// make the test fail in "SlowTests / test"
SlowTests / javaOptions += "-Dtest.init.fail=true"

// assertions
lazy val check = taskKey[Unit]("check")
check := {
  val slowtestJavaOptions = (SlowTests / javaOptions).value
  val slowtestJavaOptionsInTest = (SlowTests / test / javaOptions).value
  val slowtestJavaOptionsInRun = (SlowTests / run / javaOptions).value
  val slowtestForkOptions = (SlowTests / forkOptions).value
  val slowtestForkOptionsInTest = (SlowTests / test / forkOptions).value
  val slowtestForkOptionsInRun = (SlowTests / run / forkOptions).value
  val testForkOptions = (Test / forkOptions).value

  assert(slowtestJavaOptions.nonEmpty, "Slowtests / javaOptions should not be empty")
  assert(slowtestJavaOptionsInTest.nonEmpty, "Slowtests / test / javaOptions should not be empty")
  assert(slowtestJavaOptionsInRun.nonEmpty, "Slowtests / run / javaOptions should not be empty")
  assert(slowtestForkOptions.runJVMOptions.nonEmpty, "(Slowtests / forkOptions).runJVMOptions should not be empty")
  assert(slowtestForkOptionsInTest.runJVMOptions.nonEmpty, "Slowtests / test / forkOptions).runJVMOptions should not be empty")
  assert(slowtestForkOptionsInRun.runJVMOptions.nonEmpty, "Slowtests / run / forkOptions).runJVMOptions should not be empty")
  assert(testForkOptions.runJVMOptions.isEmpty, "test forkOptions should be empty")

  // check if tests are properly detected
  val testTestNames = (Test / definedTestNames).value
  val slowtestTestNames = (SlowTests / definedTestNames).value

  assert(testTestNames.length == 1, "Test / definedTestNames has no entries. Should have one entry 'foo.test.FooTest'")
  assert(testTestNames.head == "foo.test.FooTest", "Test / definedTestNames one entry 'foo.test.FooTest'")
  assert(slowtestTestNames.length == 1, "SlowTests / definedTestNames has no entries. Should have one entry 'foo.test.FooTest'")
  assert(slowtestTestNames.head == "foo.test.FooTest", "SlowTests / definedTestNames one entry 'foo.test.FooTest'")

}