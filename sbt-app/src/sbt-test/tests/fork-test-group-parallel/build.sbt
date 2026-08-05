val specs = "org.specs2" %% "specs2-core" % "4.3.4"
ThisBuild / scalaVersion := "2.12.21"

Global / concurrentRestrictions := Seq(Tags.limitAll(4))
libraryDependencies += specs % Test
inConfig(Test)(Seq(
  testGrouping := Def.uncached {
    val home = javaHome.value
    val strategy = outputStrategy.value
    val baseDir = baseDirectory.value
    val options = javaOptions.value
    val connect = connectInput.value
    val vars = envVars.value
    definedTests.value.map { test => new Tests.Group(test.name, Seq(test), Tests.SubProcess(
      ForkOptions(
        javaHome = home,
        outputStrategy = strategy,
        bootJars = Vector(),
        workingDirectory = Some(baseDir),
        runJVMOptions = options.toVector,
        connectInput = connect,
        envVars = vars
      )
    ))}
  },
  TaskKey[Unit]("test-failure") := Def.uncached {
    testFull.failure.value
    ()
  }
))

// No setting names how many forked test groups may run at once, so this asserts on the rule set.
// Def.uncached because sbt 2.x wants a JsonFormat for a cached task's inputs, and Tags.Rule has
// none.
val checkForkLimit = inputKey[Unit]("The rule set permits exactly the given number of forked groups")

checkForkLimit := Def.uncached {
  val want = Def.spaceDelimited("<n>").parsed.head.toInt
  // Capped by the processor count because Tags.limitAll is, and no rule can raise it.
  val cores = java.lang.Runtime.getRuntime.availableProcessors
  val n = math.min(want, cores)
  val permits = Tags.predicate((Global / concurrentRestrictions).value)
  def allows(k: Int): Boolean = permits(Map(Tags.ForkedTestGroup -> k, Tags.All -> k))
  if (!allows(n)) sys.error(s"expected $n concurrent forked test groups to be permitted, and they are not")
  if (n < cores && allows(n + 1))
    sys.error(s"expected at most $n concurrent forked test groups, but ${n + 1} is permitted")
  streams.value.log.info(s"the rule set permits $n concurrent forked test group(s)")
}
