// 6 test classes, 3 workers: splitTestGroupDefault buckets classes round-robin by name,
// so this should fork exactly 3 JVMs of 2 classes each -- not 1 (unsplit) and not 6 (one per class).
val expectedWorkers = 3
val classCount = 6

@transient
val check = TaskKey[Unit]("check", "Check tests were split across the expected number of JVMs.")

scalaVersion := "3.8.4"
organization := "com.example"

Test / fork := true
testForkedWorker := expectedWorkers

libraryDependencies += "org.scalameta" %% "munit" % "1.0.4" % Test

check := {
  val seen = (1 to classCount).map(i => file(s"seen-$i"))
  val peers = (1 to classCount).map(i => file(s"peers-$i"))
  val (existSeen, absentSeen) = seen.partition(_.exists)
  if absentSeen.nonEmpty then
    sys.error("Files were not created:\n\t" + absentSeen.mkString("\n\t"))

  // The split itself: exactly `expectedWorkers` distinct JVMs ran the 6 classes -- not 1
  // (unsplit) and not 6 (one JVM per class).
  val pids = existSeen.map(f => IO.read(f)).toSet
  if pids.size != expectedWorkers then
    sys.error(s"Expected $expectedWorkers distinct forked JVMs but saw ${pids.size}: $pids")

  // Concurrency, not just distinctness: at least one class must have observed all
  // `expectedWorkers` JVMs announced at once, proving they overlapped in time rather than
  // running the 3 groups one after another through the default's own limit.
  val maxPeers = peers.map(f => IO.read(f).trim.toInt).max
  if maxPeers < expectedWorkers then
    sys.error(s"Expected to see $expectedWorkers JVMs running at once, but max observed was $maxPeers")

  (seen ++ peers).foreach(_.delete())
}
