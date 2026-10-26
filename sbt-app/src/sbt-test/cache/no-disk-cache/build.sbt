import sbt.internal.util.{ CacheEventSummary, StringVirtualFile1 }
import sjsonnew.BasicJsonProtocol.*

val pure1 = taskKey[Unit]("")
val map1 = taskKey[String]("")
val checkNoHits = taskKey[Unit]("")

Global / localCacheDirectory := baseDirectory.value / "no-disk-cache"

cacheStores := Nil

pure1 := {
  val output = StringVirtualFile1("${OUT}/a.txt", "foo")
  Def.declareOutput(output)
  ()
}

map1 := {
  pure1.value
  val output = StringVirtualFile1("${OUT}/b.txt", "bar")
  Def.declareOutput(output)
  "something"
}

checkNoHits := Def.uncached {
  val prev = Def.cacheConfiguration.value.cacheEventLog.previous match
    case s: CacheEventSummary.Data => s
    case s                         => sys.error("empty event log")
  assert(prev.hitCount == 0, s"prev.hitCount = ${prev.hitCount} (expected 0)")
  assert(prev.errorCount.isEmpty, s"prev.errorCount = ${prev.errorCount} (expected None)")
}
