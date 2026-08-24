ThisBuild / scalaVersion := "2.12.21"

libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test

Test / fork := true

// Capped by the processor count because Tags.limitAll is, so the expectation holds on a one-core
// container as well as a large machine, where it degrades to a trivially true assertion.
val expectedJvms = settingKey[Int]("How many JVMs the group should spread over")
expectedJvms := math.min(2, java.lang.Runtime.getRuntime.availableProcessors)

Test / javaOptions += s"-Dexpect.jvms=${expectedJvms.value}"

// Global is where a build turns stealing on for every project. It is read at the test task's own
// scope, so this delegates down to each project unless one overrides it — scenario 5 below.
Global / testForkedWorkStealing := true

// The fan-out ceiling is however many concurrent forked test groups this admits. Two, to keep the
// assertions below machine-independent. Replacing the Seq rather than appending, because += cannot
// raise a limit: rules are anded together and the default already says one.
Global / concurrentRestrictions := Seq(
  Tags.limitAll(java.lang.Runtime.getRuntime.availableProcessors),
  Tags.limit(Tags.ForkedTestGroup, 2),
  Tags.exclusiveGroup(Tags.Clean)
)

// A second framework, defined in this project's test sources. The queue keeps one sub-queue per
// framework, so this exercises indices addressing the whole class list rather than one framework's
// slice.
Test / testFrameworks += TestFramework("MarkedFramework")

// Records every endGroup, so the crash scenario can assert that the suite whose JVM died is
// reported.
Test / testListeners += {
  val dir = baseDirectory.value / "reported"
  new TestReportListener {
    private def record(name: String, outcome: String): Unit = {
      dir.mkdirs()
      IO.write(dir / name, outcome)
    }
    def startGroup(name: String): Unit = ()
    def testEvent(event: TestEvent): Unit = ()
    def endGroup(name: String, t: Throwable): Unit = record(name, "throwable")
    def endGroup(name: String, result: TestResult): Unit = record(name, result.toString)
  }
}

// Records doInit and doComplete. Each call writes its own file, so counting is not a race even when
// the calls come from several worker threads at once, which is the bug being guarded against.
Test / testListeners += {
  val dir = baseDirectory.value / "lifecycle"
  val seq = new java.util.concurrent.atomic.AtomicInteger(0)
  // Tagged with this listener's identity as well as a counter: testListeners is a task key, so each
  // evaluation gets a fresh counter while the directory persists, and without the tag a second run
  // would overwrite the first's files.
  val tag = Integer.toHexString(System.identityHashCode(seq))
  new TestsListener {
    private def mark(kind: String): Unit = {
      dir.mkdirs()
      IO.touch(dir / s"$kind-$tag-${seq.getAndIncrement()}")
    }
    def doInit(): Unit = mark("init")
    def doComplete(finalResult: TestResult): Unit = mark("complete")
    def startGroup(name: String): Unit = ()
    def testEvent(event: TestEvent): Unit = ()
    def endGroup(name: String, t: Throwable): Unit = ()
    def endGroup(name: String, result: TestResult): Unit = ()
  }
}

// A to D are junit classes, E and F belong to the second framework.
val allSuites = ('A' to 'F').map(_.toString).toSet

val check = taskKey[Unit]("Every class is leased exactly once and runs within the JVM ceiling")

check := {
  val base = baseDirectory.value
  // doInit and doComplete are once per test group, not once per worker JVM.
  val lifecycle = IO.listFiles(base / "lifecycle").toSeq.map(_.getName.takeWhile(_ != '-'))
  val counts = lifecycle.groupBy(identity).map { case (k, v) => k -> v.size }
  if (counts != Map("init" -> 1, "complete" -> 1))
    sys.error(s"expected one doInit and one doComplete for the group, got: $counts")
  // Each marker is `<suite>.<pid>`, so these are plain facts about what ran where, with no timing.
  val runs = IO.listFiles(base / "pids").toSeq.map { f =>
    val n = f.getName
    val i = n.lastIndexOf('.')
    (n.substring(0, i), n.substring(i + 1))
  }
  val bySuite = runs.groupBy(_._1)
  val missing = allSuites -- bySuite.keySet
  if (missing.nonEmpty) sys.error(s"suites that never ran: $missing")
  val dup = bySuite.filter(_._2.size > 1)
  if (dup.nonEmpty)
    sys.error(s"suites handed out more than once: ${dup.map { case (s, r) => s -> r.map(_._2) }}")
  val pids = runs.map(_._2).distinct
  val want = expectedJvms.value
  // Exactly this many, not at most: the ceiling caps it above, and suite A refuses to finish until
  // that many JVMs have recorded work, so a run that used fewer has already failed by here.
  if (pids.size != want) sys.error(s"expected exactly $want test JVMs, saw ${pids.size}: $pids")
  // One JUnit report per class, holding one test case: a worker runs the classes it leases
  // concurrently and sbt hands every one of their events to the listeners on the single thread
  // reading that worker's connection, so a listener keying the open suite by thread wrote one
  // report for the whole group, named after whichever class started last.
  val reportDir = (Test / testReportsDirectory).value
  val cases = allSuites.toSeq.sorted.map { s =>
    val f = reportDir / s"TEST-$s.xml"
    s -> (if (f.exists) IO.read(f).sliding("<testcase".length).count(_ == "<testcase") else -1)
  }
  if (cases.exists(_._2 != 1))
    sys.error(s"each class must have its own report with only its own test case, got: $cases")
  streams.value.log.info(s"${runs.size} suites ran exactly once across ${pids.size} JVMs")
}

val checkThreads = taskKey[Unit]("Turning on stealing does not change testForkedParallelism")

checkThreads := {
  // The thread count is decided per group, inside ForkTests, so it cannot leak into a build-wide
  // setting: a derived testForkedParallelism could only read the Global flag.
  val p = (Test / testForkedParallelism).value
  if (p != None)
    sys.error(s"testForkedParallelism must stay at its own default with stealing on, got: $p")
  streams.value.log.info("testForkedParallelism is untouched by testForkedWorkStealing")
}

val checkSerial = taskKey[Unit]("parallelExecution := false pins the whole group to one JVM")

checkSerial := {
  // No timing here: the JVM count is decided before anything forks, so a group that is not spread
  // forks exactly one JVM and every marker carries its pid. Seeing one means the group was never
  // spread, which is what disabling parallel execution has to mean.
  val runs = IO.listFiles(baseDirectory.value / "pids").toSeq.map(_.getName)
  val suites = runs.map(n => n.substring(0, n.lastIndexOf('.'))).toSet
  val pids = runs.map(n => n.substring(n.lastIndexOf('.') + 1)).distinct
  if (suites != allSuites) sys.error(s"expected every suite to run, got: $suites")
  if (pids.size != 1)
    sys.error(s"parallelExecution is off, so the group must run in one JVM, saw ${pids.size}: $pids")
  streams.value.log.info(s"serial run used ${pids.size} JVM")
}

val checkSpread = taskKey[Unit]("testForkedParallel off still lets the group be leased across JVMs")

checkSpread := {
  // No timing here either: suite A refuses to finish until `expectedJvms` JVMs have recorded work, so a run
  // that used fewer has already failed before this task.
  val runs = IO.listFiles(baseDirectory.value / "pids").toSeq.map(_.getName)
  val suites = runs.map(n => n.substring(0, n.lastIndexOf('.'))).toSet
  val pids = runs.map(n => n.substring(n.lastIndexOf('.') + 1)).distinct
  val want = expectedJvms.value
  if (suites != allSuites) sys.error(s"expected every suite to run, got: $suites")
  if (pids.size != want)
    sys.error(
      s"testForkedParallel governs the threads inside a worker, not how many workers a group gets: " +
        s"expected $want test JVMs, saw ${pids.size}: $pids"
    )
  streams.value.log.info(s"group spread over ${pids.size} JVMs with testForkedParallel off")
}

val checkOptOut = taskKey[Unit]("A project may turn stealing off at its own scope while Global has it on")

checkOptOut := {
  // A project whose suites clobber shared external state has to keep them out of each other's way
  // even though the build steals build-wide. Turning the flag off in this project's Test scope must
  // leave the group unspread: one JVM, every suite in it. No timing — the JVM count is fixed before
  // anything forks, so one pid means the queue never served a second worker.
  val runs = IO.listFiles(baseDirectory.value / "pids").toSeq.map(_.getName)
  val suites = runs.map(n => n.substring(0, n.lastIndexOf('.'))).toSet
  val pids = runs.map(n => n.substring(n.lastIndexOf('.') + 1)).distinct
  if (suites != allSuites) sys.error(s"expected every suite to run, got: $suites")
  if (pids.size != 1)
    sys.error(
      s"Test / testForkedWorkStealing := false must override the Global flag and keep the group " +
        s"in one JVM, saw ${pids.size}: $pids"
    )
  streams.value.log.info(s"the project opted out of stealing and used ${pids.size} JVM")
}

val checkOverlap = taskKey[Unit]("A class matching two frameworks keeps the whole group in one JVM")

checkOverlap := {
  // No timing here either: the JVM count is decided before anything forks, so seeing one pid means
  // the group was never spread.
  val runs = IO.listFiles(baseDirectory.value / "pids").toSeq.map(_.getName)
  val suites = runs.map(n => n.substring(0, n.lastIndexOf('.'))).toSet
  val pids = runs.map(n => n.substring(n.lastIndexOf('.') + 1)).distinct
  if (suites != allSuites) sys.error(s"expected every suite to run, got: $suites")
  if (pids.size != 1)
    sys.error(
      s"E and F match two frameworks, so the group must stay in one JVM, saw ${pids.size}: $pids"
    )
  streams.value.log.info(s"overlapping fingerprints kept the group in ${pids.size} JVM")
}

val checkCrash = taskKey[Unit]("A worker that dies mid-suite reports that suite as an error")

checkCrash := {
  val reported =
    IO.listFiles(baseDirectory.value / "reported").map(f => f.getName -> IO.read(f).trim).toMap
  // Only A is asserted: killing a worker poisons the shared queue, so classes not yet leased are
  // legitimately skipped and asserting on them would be a race.
  reported.get("A") match {
    case None =>
      sys.error(s"the crashed suite A was never reported; reported: ${reported.keySet}")
    case Some(outcome) if !outcome.toLowerCase.contains("error") =>
      sys.error(s"the crashed suite A should be reported as an error, got: $outcome")
    case Some(outcome) =>
      streams.value.log.info(s"crashed suite reported as $outcome")
  }
}
