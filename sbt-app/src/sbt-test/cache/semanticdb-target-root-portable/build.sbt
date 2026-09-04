import sbt.internal.util.CacheEventSummary
import java.nio.file.Paths

lazy val checkHit = taskKey[Unit]("asserts the previous command was a pure cache hit")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

ThisBuild / scalaVersion := "3.8.4"
ThisBuild / semanticdbEnabled := true

lazy val root = project.in(file("."))

// -semanticdb-target reaches the cache keys as an absolute path too, which would move them by
// itself; virtualize it so the target root is the only input left free to move.
Compile / semanticdbOptions := {
  val conv = fileConverter.value
  val prev = (Compile / semanticdbOptions).value
  prev.zipWithIndex.map { case (opt, i) =>
    if (i > 0 && prev(i - 1) == "-semanticdb-target") conv.toVirtualFile(Paths.get(opt)).id
    else opt
  }
}

checkHit := Def.uncached {
  val config = Def.cacheConfiguration.value
  val prev = config.cacheEventLog.previous match
    case s: CacheEventSummary.Data => s
    case _                         => sys.error("empty event log")
  streams.value.log.info(s"hitCount=${prev.hitCount} missCount=${prev.missCount}")
  assert(prev.missCount == 0, s"expected a pure cache hit but missCount=${prev.missCount}")
}
