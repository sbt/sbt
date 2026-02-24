import sbt.internal.util.CacheEventSummary
import complete.DefaultParsers.*

lazy val checkMiss = inputKey[Unit]("")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

scalaVersion := "3.8.2"
checkMiss := {
  val expected: Int = (Space ~> NatBasic).parsed
  val s = streams.value
  val config = Def.cacheConfiguration.value
  val prev = config.cacheEventLog.previous match
    case s: CacheEventSummary.Data => s
    case _                         => sys.error(s"empty event log")
  s.log.info(prev.missCount.toString) 
  assert(prev.missCount > 0, s"prev.missCount = ${prev.missCount}")
}
