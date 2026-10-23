import sbt.internal.util.CacheEventSummary

lazy val checkHit = taskKey[Unit]("asserts the previous command was a pure cache hit")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

ThisBuild / scalaVersion := "3.8.4"
ThisBuild / semanticdbEnabled := true

lazy val root = project.in(file("."))

checkHit := Def.uncached {
  val config = Def.cacheConfiguration.value
  val prev = config.cacheEventLog.previous match
    case s: CacheEventSummary.Data => s
    case _                         => sys.error("empty event log")
  streams.value.log.info(s"hitCount=${prev.hitCount} missCount=${prev.missCount}")
  assert(prev.missCount == 0, s"expected a pure cache hit but missCount=${prev.missCount}")
}
