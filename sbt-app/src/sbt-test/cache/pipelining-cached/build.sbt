import sbt.internal.util.CacheEventSummary

@transient
lazy val checkHit = taskKey[Unit]("asserts the previous command was a pure cache hit")

@transient
lazy val checkOptions = taskKey[Unit]("asserts scalacOptions carry no machine output paths")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

scalaVersion := "3.9.0"
usePipelining := true
semanticdbEnabled := true

lazy val a = project
lazy val b = project.dependsOn(a)

LocalRootProject / checkOptions := {
  val out = rootOutputDirectory.value.toString
  val opts = (a / Compile / scalacOptions).value ++ (b / Test / scalacOptions).value
  assert(opts.contains("-Ypickle-write"), opts.toString)
  assert(opts.contains("-semanticdb-target"), opts.toString)
  assert(!opts.exists(_.contains(out)), opts.toString)
}

LocalRootProject / checkHit := {
  val config = Def.cacheConfiguration.value
  val prev = config.cacheEventLog.previous match
    case s: CacheEventSummary.Data => s
    case _                         => sys.error("empty event log")
  streams.value.log.info(s"hitCount=${prev.hitCount} missCount=${prev.missCount}")
  assert(prev.missCount == 0, s"expected a pure cache hit but missCount=${prev.missCount}")
}
