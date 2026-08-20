import sbt.internal.util.CacheEventSummary

val delClasses = taskKey[Unit]("deletes the classes directory")
val delJar = taskKey[Unit]("deletes the packaged jar")
val saveJar = taskKey[Unit]("copies the packaged jar aside for later comparison")
val checkNoMiss = taskKey[Unit]("asserts the previous command was served entirely from the cache")
val checkClasses = taskKey[Unit]("asserts class files exist")
val checkJarMatches = taskKey[Unit]("asserts the jar is byte-identical to the saved one")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

// A distinct project id keeps this fixture's output paths from colliding with same-named
// sibling fixtures in scripted's shared batch directory.
lazy val inputOrder = project
  .in(file("."))
  .settings(
    scalaVersion := "3.8.4",
    // Reversing stands in for a second filesystem's readdir order, which is the only part of
    // this that cannot be reproduced on a single machine.
    Compile / sources := {
      val ss = (Compile / sources).value
      if ((baseDirectory.value / "reverse.marker").exists) ss.reverse else ss
    },
    Compile / packageBin / mappings := {
      val ms = (Compile / packageBin / mappings).value
      if ((baseDirectory.value / "reverse.marker").exists) ms.reverse else ms
    },
  )

delClasses := Def.uncached {
  val dir = (inputOrder / Compile / classDirectory).value
  IO.delete(dir)
  streams.value.log.info(s"deleted $dir")
}

delJar := Def.uncached {
  val conv = fileConverter.value
  val jar = conv.toPath((inputOrder / Compile / packageBin / artifactPath).value).toFile()
  IO.delete(jar)
  streams.value.log.info(s"deleted $jar")
}

saveJar := Def.uncached {
  val conv = fileConverter.value
  val jar = conv.toPath((inputOrder / Compile / packageBin).value).toFile()
  IO.copyFile(jar, baseDirectory.value / "expected.jar")
}

checkJarMatches := Def.uncached {
  val conv = fileConverter.value
  val jar = conv.toPath((inputOrder / Compile / packageBin).value).toFile()
  val expected = baseDirectory.value / "expected.jar"
  assert(
    java.util.Arrays.equals(IO.readBytes(jar), IO.readBytes(expected)),
    s"jar bytes changed after reordering packageBin / mappings"
  )
}

checkNoMiss := Def.uncached {
  val config = Def.cacheConfiguration.value
  val prev = config.cacheEventLog.previous match
    case data: CacheEventSummary.Data => data
    case _                            => sys.error("empty event log")
  streams.value.log.info(s"prev hitCount=${prev.hitCount} missCount=${prev.missCount}")
  assert(prev.missCount == 0, s"expected no cache misses but missCount=${prev.missCount}")
}

checkClasses := Def.uncached {
  val dir = (inputOrder / Compile / classDirectory).value
  val classes = (dir ** "*.class").get()
  assert(classes.nonEmpty, s"no class files under $dir")
}
