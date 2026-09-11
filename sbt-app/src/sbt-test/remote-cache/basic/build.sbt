import sbt.internal.util.CacheEventSummary

scalaVersion := "3.9.0"

Global / remoteCache := Some(new java.net.URI("grpc://127.0.0.1:2024"))
Global / localCacheDirectory := baseDirectory.value / "diskcache"

val checkHit = taskKey[Unit]("asserts the previous compile was forced to recompute by clean")

checkHit := Def.uncached {
  val config = Def.cacheConfiguration.value
  val prev = config.cacheEventLog.previous match
    case data: CacheEventSummary.Data => data
    case _                            => sys.error("empty event log")
  streams.value.log.info(
    s"prev hitCount=${prev.hitCount} missCount=${prev.missCount} remoteHitCount=${prev.remoteHitCount}"
  )
  // clean invalidates this subproject's disk cache for one command, forcing a full recompute
  // even though a matching remote cache entry exists: clean isn't meant to be defeated by
  // either cache backend.
  assert(prev.missCount > 0, s"expected clean to force a miss but missCount=${prev.missCount}")
  assert(prev.remoteHitCount == 0, s"expected no remote hits but remoteHitCount=${prev.remoteHitCount}")
}
