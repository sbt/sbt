import sbt.internal.util.CacheEventSummary
import sbt.util.BuildWideCacheConfiguration

@transient
lazy val checkMiss = taskKey[Unit]("assert the previous command included a cache miss")
@transient
lazy val checkHit = taskKey[Unit]("assert the previous command was a cache hit with no misses")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

scalaVersion := "3.9.0"

checkMiss := {
  val prev = previousSummary(streams.value, Def.cacheConfiguration.value)
  assert(prev.missCount > 0, s"expected a cache miss but missCount=${prev.missCount}")
}

checkHit := {
  val prev = previousSummary(streams.value, Def.cacheConfiguration.value)
  assert(prev.hitCount > 0, s"expected a cache hit but hitCount=${prev.hitCount}")
  assert(prev.missCount == 0, s"expected no cache miss but missCount=${prev.missCount}")
}

lazy val foo = project
lazy val bar = project

def previousSummary(s: TaskStreams, config: BuildWideCacheConfiguration): CacheEventSummary.Data =
  val prev = config.cacheEventLog.previous match
    case d: CacheEventSummary.Data => d
    case _                         => sys.error("empty event log")
  s.log.info(s"hitCount=${prev.hitCount} missCount=${prev.missCount}")
  prev
