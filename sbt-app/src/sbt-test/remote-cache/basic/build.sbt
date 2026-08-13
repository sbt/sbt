import sbt.internal.util.CacheEventSummary

scalaVersion := "3.8.4"

Global / remoteCache := Some(new java.net.URI("grpc://127.0.0.1:2024"))
Global / localCacheDirectory := baseDirectory.value / "diskcache"

val checkHit = taskKey[Unit]("asserts the previous compile was served from the remote cache")

checkHit := Def.uncached {
  val config = Def.cacheConfiguration.value
  val prev = config.cacheEventLog.previous match
    case data: CacheEventSummary.Data => data
    case _                            => sys.error("empty event log")
  streams.value.log.info(
    s"prev hitCount=${prev.hitCount} missCount=${prev.missCount} remoteHitCount=${prev.remoteHitCount}"
  )
  assert(prev.missCount == 0, s"expected 100% hit rate but missCount=${prev.missCount}")
  assert(
    prev.remoteHitCount == prev.hitCount,
    s"expected 100% remote hit rate but remoteHitCount=${prev.remoteHitCount} hitCount=${prev.hitCount}"
  )
}
