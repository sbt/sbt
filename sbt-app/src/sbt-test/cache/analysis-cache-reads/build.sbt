ThisBuild / scalaVersion := "3.3.6"

// The compiles below have to be real ones: an analysis served from the action cache was never
// written, so there is nothing for the cache to have kept. Keeping the cache inside the sandbox
// means each run starts without one, rather than inheriting whatever an earlier run compiled.
// Global, because that is the scope cacheStores resolves it in.
Global / localCacheDirectory := (ThisBuild / baseDirectory).value / "target" / "local-cache"

lazy val lib = project
lazy val app = project.dependsOn(lib)

val recordAnalysisReads = taskKey[Unit]("Marks the analysis cache read counts.")
val checkAnalysisServedFromMemory =
  taskKey[Unit]("Fails if any analysis was read from disk since the mark.")
val checkAnalysisNotDeserialized =
  taskKey[Unit]("Fails if any analysis was deserialized since the mark.")

recordAnalysisReads := Def.uncached(AnalysisCacheReads.record())

checkAnalysisServedFromMemory := Def.uncached {
  val (served, hashed, deserialized) = AnalysisCacheReads.since()
  assert(
    served > 0,
    s"expected the compile to read an analysis, but it read none"
  )
  assert(
    hashed == 0 && deserialized == 0,
    s"expected every analysis read to be answered from memory, " +
      s"but $hashed hashed the file and $deserialized deserialized it"
  )
}

checkAnalysisNotDeserialized := Def.uncached {
  val (served, hashed, deserialized) = AnalysisCacheReads.since()
  assert(
    served > 0,
    s"expected the compile to read an analysis, but it read none"
  )
  assert(
    deserialized == 0,
    s"expected the analysis just written to be served from memory, " +
      s"but $deserialized of ${served + deserialized} reads deserialized it"
  )
}
