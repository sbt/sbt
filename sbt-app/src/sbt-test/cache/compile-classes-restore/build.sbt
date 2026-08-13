import sbt.internal.util.CacheEventSummary

val checkCompileHit = taskKey[Unit]("asserts the previous command took cache hits")
val delClasses = taskKey[Unit]("deletes the classes directory")
val delClassesZip = taskKey[Unit]("deletes the sibling classes.sbtdir.zip")
val checkClasses = taskKey[Unit]("asserts .class files exist")
val checkNoClasses = taskKey[Unit]("asserts no .class files exist")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

// A distinct project id keeps this fixture's output paths from colliding with same-named
// sibling fixtures in scripted's shared batch directory.
lazy val compileClassesRestore = project
  .in(file("."))
  .settings(
    scalaVersion := "3.8.4"
  )

delClasses := Def.uncached {
  val dir = (Compile / classDirectory).value
  IO.delete(dir)
  streams.value.log.info(s"deleted $dir")
}

delClassesZip := Def.uncached {
  val dir = (Compile / classDirectory).value
  val zip = new java.io.File(dir.getParentFile, dir.getName + ".sbtdir.zip")
  streams.value.log.info(s"deleting $zip (exists=${zip.exists})")
  IO.delete(zip)
}

checkClasses := Def.uncached {
  val dir = (Compile / classDirectory).value
  val classes = (dir ** "*.class").get()
  streams.value.log.info(s"classes under $dir: ${classes.mkString(", ")}")
  assert(classes.nonEmpty, s"no class files under $dir")
}

checkNoClasses := Def.uncached {
  val dir = (Compile / classDirectory).value
  val classes = (dir ** "*.class").get()
  streams.value.log.info(s"classes under $dir: ${classes.mkString(", ")}")
  assert(classes.isEmpty, s"unexpected class files under $dir: ${classes.mkString(", ")}")
}

checkCompileHit := Def.uncached {
  val config = Def.cacheConfiguration.value
  val prev = config.cacheEventLog.previous match
    case s: CacheEventSummary.Data => s
    case _                         => sys.error("empty event log")
  streams.value.log.info(s"prev hitCount=${prev.hitCount} missCount=${prev.missCount}")
  assert(prev.hitCount >= 1, s"expected cache hits but hitCount=${prev.hitCount}")
}
