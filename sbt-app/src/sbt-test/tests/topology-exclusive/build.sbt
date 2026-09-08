import java.util.concurrent.atomic.AtomicInteger
import Tests._

lazy val exclusivePhases = new AtomicInteger(0)
def probe(name: String): Unit = probeWith(exclusivePhases, "overlaps.log", name)
lazy val aPhaseActive = new AtomicInteger(0)

@transient val checkNoOverlap =
  taskKey[Unit]("Fail if any subproject's setup/cleanup phase overlapped another's")
@transient val otherWork = taskKey[Unit]("Stand-in for unrelated, non-test build work")
@transient val checkConcurrentWithOtherWork =
  taskKey[Unit]("Fail unless a's exclusive phase and unrelated work were seen running at the same time")
@transient val allWork =
  taskKey[Unit]("Run both subprojects' testFull and otherWork as concurrent dependencies")

Global / workerMaxInstances := 2

scalaVersion := "3.8.4"
organization := "com.example"

lazy val a = project
  .settings(
    testTopology := TestTopology.subprojectExclusive,
    Test / testOptions += Tests.Setup(() => {
      aPhaseActive.incrementAndGet()
      probe("a setup")
      aPhaseActive.decrementAndGet()
      ()
    }),
    Test / testOptions += Tests.Cleanup(() => {
      aPhaseActive.incrementAndGet()
      probe("a cleanup")
      aPhaseActive.decrementAndGet()
      ()
    }),
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.4" % Test,
  )
lazy val b = project
  .settings(
    testTopology := TestTopology.subprojectExclusive,
    Test / testOptions += Tests.Setup(() => probe("b setup")),
    Test / testOptions += Tests.Cleanup(() => probe("b cleanup")),
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.4" % Test,
  )

lazy val root = (project in file("."))
  .autoAggregate
  .settings(
    checkNoOverlap / aggregate := false,
    otherWork / aggregate := false,
    checkConcurrentWithOtherWork / aggregate := false,
    allWork / aggregate := false,
    checkNoOverlap := {
      val found = readAndClear("overlaps.log")
      if found.nonEmpty then
        sys.error(
          "SubprojectExclusive should fully serialize each subproject's setup-through-cleanup phase, but:\n\t"
            + found.mkString("\n\t")
        )
    },
    otherWork := {
      val deadline = System.currentTimeMillis() + 8000
      var witnessed = false
      while System.currentTimeMillis() < deadline do
        if aPhaseActive.get() > 0 then witnessed = true
        Thread.sleep(50)
      if witnessed then IO.append(file("concurrent-with-other.log"), "otherWork witnessed a's exclusive phase\n")
      ()
    },
    checkConcurrentWithOtherWork := {
      val found = readAndClear("concurrent-with-other.log")
      if found.isEmpty then
        sys.error(
          "Expected a's exclusive test phase and unrelated work to run concurrently at some point, " +
            "but they never overlapped -- exclusiveGroupWithin may be blocking more than just other tests."
        )
    },
    allWork := {
      (a / Test / testFull).value
      (b / Test / testFull).value
      otherWork.value
      ()
    },
  )

def probeWith(counter: AtomicInteger, log: String, name: String): Unit = {
  val now = counter.incrementAndGet()
  if now > 1 then IO.append(file(log), s"$name saw $now concurrent\n")
  Thread.sleep(1000)
  counter.decrementAndGet()
  ()
}

def readAndClear(log: String): List[String] = {
  val f = file(log)
  val found = if f.exists then IO.readLines(f).filter(_.nonEmpty) else Nil
  IO.delete(f)
  found
}
