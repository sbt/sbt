import sbt.internal.util.{ CacheEventSummary, StringVirtualFile1 }
import sjsonnew.BasicJsonProtocol.*
import xsbti.compile.CompileAnalysis

val analysisKey = taskKey[CompileAnalysis]("")
val useAnalysis = taskKey[Unit]("")
val checkHit = taskKey[Unit]("")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

analysisKey := {
  (Compile / compile).value match {
    case a: sbt.internal.inc.Analysis => a
    case a                            => sys.error(s"Unexpected analysis: ${a.getClass}")
  }
}

useAnalysis := {
  analysisKey.value
  val output = StringVirtualFile1("target/out/analysis.txt", "foo")
  Def.declareOutput(output)
  ()
}

checkHit := Def.uncached {
  val config = Def.cacheConfiguration.value
  val prev = config.cacheEventLog.previous match {
    case s: CacheEventSummary.Data => s
    case _                         => sys.error("empty event log")
  }
  assert(prev.hitCount > 0, s"prev.hitCount = ${prev.hitCount}")
}
