import java.util.concurrent.atomic.AtomicLong
import sbt.protocol.testing.TestResult

val startAtNs = collection.concurrent.TrieMap.empty[String, AtomicLong]
val eventCallbackCount = collection.concurrent.TrieMap.empty[String, AtomicLong]
val maxDetailSize = collection.concurrent.TrieMap.empty[String, AtomicLong]
val endSeen = collection.concurrent.TrieMap.empty[String, AtomicLong]

def counter(map: collection.concurrent.TrieMap[String, AtomicLong], key: String): AtomicLong =
  map.getOrElseUpdate(key, new AtomicLong(0L))

def resetCounters(key: String): Unit = {
  counter(startAtNs, key).set(0L)
  counter(eventCallbackCount, key).set(0L)
  counter(maxDetailSize, key).set(0L)
  counter(endSeen, key).set(0L)
}

def streamingListener(label: String): TestReportListener = new TestReportListener {
  def startGroup(name: String): Unit =
    counter(startAtNs, label).compareAndSet(0L, System.nanoTime())

  def testEvent(event: TestEvent): Unit = {
    counter(eventCallbackCount, label).incrementAndGet()
    val detailSize = event.detail.size.toLong
    val maxSeen = counter(maxDetailSize, label)
    var done = false
    while (!done) {
      val current = maxSeen.get()
      if (detailSize <= current) done = true
      else done = maxSeen.compareAndSet(current, detailSize)
    }
  }

  def endGroup(name: String, t: Throwable): Unit =
    counter(endSeen, label).set(1L)

  def endGroup(name: String, result: TestResult): Unit =
    counter(endSeen, label).set(1L)
}

lazy val resetListener = taskKey[Unit]("Reset listener state for timing checks")
lazy val checkStreaming = taskKey[Unit]("Assert test events are received before endGroup")

ThisBuild / scalaVersion := "2.12.21"

def commonSettings(label: String): Seq[Def.Setting[_]] =
  Seq(
    libraryDependencies += "org.scala-sbt" % "test-interface" % "1.0" % Test,
    Test / testFrameworks := Seq(new TestFramework("custom.StreamingFramework")),
    Test / parallelExecution := false,
    testListeners += streamingListener(label),
    resetListener := resetCounters(label),
    checkStreaming := {
      val startNs = counter(startAtNs, label).get()
      val callbacks = counter(eventCallbackCount, label).get()
      val largestDetail = counter(maxDetailSize, label).get()
      val endWasSeen = counter(endSeen, label).get()
      if (startNs == 0L) sys.error("startGroup was never called")
      if (endWasSeen == 0L) sys.error("endGroup was never called")
      if (callbacks < 2L)
        sys.error("Expected at least two testEvent callbacks, saw " + callbacks)
      if (largestDetail > 1L)
        sys.error(
          "Expected streamed test events with detail size 1, largest detail size was " + largestDetail
        )
    }
  )

lazy val inproc = (project in file("inproc"))
  .settings(commonSettings("inproc"): _*)
  .settings(Test / fork := false)

lazy val forked = (project in file("forked"))
  .settings(commonSettings("forked"): _*)
  .settings(Test / fork := true)

lazy val root = (project in file("."))
  .aggregate(inproc, forked)
  .settings(publish / skip := true)
