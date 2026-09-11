import sbt.internal.util.{ CacheEventSummary, StringVirtualFile1 }
import sjsonnew.BasicJsonProtocol.*

val pure1 = taskKey[Unit]("")
val map1 = taskKey[String]("")
val mapN1 = taskKey[Unit]("")
val checkMapN1 = taskKey[Unit]("")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

pure1 := {
  val output = StringVirtualFile1("${OUT}/a.txt", "foo")
  Def.declareOutput(output)
  ()
}

map1 := {
  pure1.value
  val output1 = StringVirtualFile1("${OUT}/b1.txt", "foo")
  val output2 = StringVirtualFile1("${OUT}/b2.txt", "foo")
  Def.declareOutput(output1)
  Def.declareOutput(output2)
  "something"
}

mapN1 := {
  pure1.value
  map1.value
  val output = StringVirtualFile1("${OUT}/c.txt", "foo")
  Def.declareOutput(output)
  ()
}

checkMapN1 := Def.uncached {
  val s = streams.value
  val config = Def.cacheConfiguration.value
  val prev = config.cacheEventLog.previous match
    case s: CacheEventSummary.Data => s
    case s                         => sys.error(s"empty event log")
  // The preceding `clean` invalidates this subproject's disk cache for one command, so
  // mapN1's own dependencies (pure1, map1) are forced to miss here too, not resurrected from disk.
  assert(prev.hitCount == 0, s"prev.hitCount = ${prev.hitCount} (expected 0)")
}
