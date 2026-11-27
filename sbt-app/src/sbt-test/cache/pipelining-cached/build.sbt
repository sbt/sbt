import sbt.internal.util.CacheEventSummary

@transient
lazy val checkHit = taskKey[Unit]("asserts the previous command was a pure cache hit")

@transient
lazy val checkOptions = taskKey[Unit]("checks the compiler options exposed to Zinc and BSP")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

scalaVersion := "3.9.0"
usePipelining := true
semanticdbEnabled := true

lazy val a = project
lazy val b = project.dependsOn(a)

def checkOptionsFor(config: Configuration) = Def.task {
  val out = rootOutputDirectory.value.toString
  val jar = (b / config / earlyOutput).value.id
  val opts = (b / config / scalacOptions).value
  val bsp = (b / config / bspBuildTargetScalacOptionsItem).value.options
  val zinc = (b / config / compile / compileInputs).value.options.scalacOptions.toVector
  Seq(opts, bsp).foreach { exposed =>
    assert(!exposed.contains("-Ypickle-java") && !exposed.contains("-Ypickle-write"), exposed)
    assert(exposed.contains("-semanticdb-target"), exposed)
  }
  assert(zinc == Seq("-Ypickle-java", "-Ypickle-write", jar) ++ opts, zinc)
  assert(!zinc.exists(_.contains(out)), zinc)
  jar
}

LocalRootProject / checkOptions := {
  val _ = (b / Test / compile).value
  val compileJar = checkOptionsFor(Compile).value
  val testJar = checkOptionsFor(Test).value
  assert(compileJar != testJar, compileJar)
}

LocalRootProject / checkHit := {
  val config = Def.cacheConfiguration.value
  val prev = config.cacheEventLog.previous match
    case s: CacheEventSummary.Data => s
    case _                         => sys.error("empty event log")
  streams.value.log.info(s"hitCount=${prev.hitCount} missCount=${prev.missCount}")
  assert(prev.missCount == 0, s"expected a pure cache hit but missCount=${prev.missCount}")
}
