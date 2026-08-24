// Eight forked test groups, and an assertion about how many of them overlap: that raising the
// ForkedTestGroup limit genuinely lets that many groups run together, which is also the number work
// stealing reads as its fan-out ceiling. The serialising direction is covered by
// tests/fork-test-group-parallel, whose groups collide on a lock directory.
ThisBuild / scalaVersion := "2.12.21"
libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test
Test / fork := true

// Eight groups but only this many are expected to overlap, so the assertion holds on a small
// machine: sbt's task pool and Tags.limitAll are both sized by the processor count, and no rule can
// raise them.
val expectedPeak = settingKey[Int]("How many forked test groups should be seen running at once")
expectedPeak := math.min(4, java.lang.Runtime.getRuntime.availableProcessors)

Test / javaOptions += s"-Dexpect.peak=${expectedPeak.value}"

// One group per test class.
Test / testGrouping := Def.uncached {
  val base = baseDirectory.value
  val opts = (Test / javaOptions).value
  (Test / definedTests).value.map { t =>
    new Tests.Group(
      t.name,
      Seq(t),
      Tests.SubProcess(ForkOptions().withWorkingDirectory(Some(base)).withRunJVMOptions(opts.toVector))
    )
  }
}

val checkPeak = taskKey[Unit]("Every group arrived; the barrier in the tests is what proved the overlap")

checkPeak := Def.uncached {
  // The overlap is proved inside the run, not here: Barrier.arrive fails its class unless it can see
  // `expect.peak` arrivals, and the marks are append-only, so the Nth arrival means those N are all
  // still sitting in the barrier. There is nothing left for this task to check about the peak -- the
  // markers carry no times. What it adds is that no group was skipped, which would otherwise surface
  // as a puzzling barrier timeout in whichever groups did run rather than as a missing group.
  val arrived = IO.listFiles(baseDirectory.value / "arrivals").map(_.getName).toSet
  val missing = (0 until 8).map("G" + _).toSet -- arrived
  if (missing.nonEmpty) sys.error(s"groups that never ran: ${missing.toSeq.sorted}")
  streams.value.log.info(
    s"all 8 forked test groups ran, ${expectedPeak.value} of them in the barrier together"
  )
}
