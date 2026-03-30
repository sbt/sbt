import sbt.internal.util.CacheEventSummary

lazy val checkHit = taskKey[Unit]("")
lazy val verify = "com.eed3si9n.verify" %% "verify" % "1.0.0"

Global / localCacheDirectory := baseDirectory.value / "diskcache"

scalaVersion := "3.8.3"
libraryDependencies += verify % Test
testFrameworks += new TestFramework("verify.runner.Framework")

checkHit := {
  val s = streams.value
  val config = Def.cacheConfiguration.value
  val prev = config.cacheEventLog.previous match
    case s: CacheEventSummary.Data => s
    case _                         => sys.error(s"empty event log")
  assert(prev.missCount == 0, s"prev.missCount = ${prev.missCount}")
}
