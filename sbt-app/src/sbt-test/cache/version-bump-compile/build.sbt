import sbt.internal.util.CacheEventSummary
import complete.DefaultParsers.*

Global / localCacheDirectory := baseDirectory.value / "diskcache"

scalaVersion := "3.8.4"

lazy val checkMiss = inputKey[Unit]("Assert the exact onsite/miss count of the previous run")

lazy val foo = project

lazy val app = project.dependsOn(foo)

lazy val root = (project in file("."))
  .aggregate(foo, app)
  .settings(
    checkMiss := {
      val expected: Int = (Space ~> NatBasic).parsed
      val s = streams.value
      val config = Def.cacheConfiguration.value
      val prev = config.cacheEventLog.previous match
        case d: CacheEventSummary.Data => d
        case CacheEventSummary.Empty   => sys.error("empty event log")
      s.log.info(s"missCount = ${prev.missCount}")
      assert(prev.missCount == expected, s"prev.missCount = ${prev.missCount} (expected $expected)")
    }
  )
