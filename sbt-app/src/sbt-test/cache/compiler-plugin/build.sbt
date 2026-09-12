import sbt.internal.util.CacheEventSummary

val check = taskKey[Unit]("")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

scalaVersion := "2.13.16"

addCompilerPlugin(("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.full))
check := Def.uncached {
  val s = streams.value
  val config = Def.cacheConfiguration.value
  val prev = config.cacheEventLog.previous match
    case s: CacheEventSummary.Data => s
    case s                         => sys.error(s"empty event log")
  s.log.info(s"prev.missCount = ${prev.missCount}")
  assert(prev.missCount == 0, s"prev.missCount = ${prev.missCount}")
}

lazy val foo = (project in file("foo"))
  .settings(
    name := "foo"
  )
