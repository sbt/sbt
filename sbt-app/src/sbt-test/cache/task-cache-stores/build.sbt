val mark = settingKey[File]("")
val markTime = taskKey[Unit]("")
val delClasses = taskKey[Unit]("")
val delSrcJar = taskKey[Unit]("")
val checkClassesRecompiled = taskKey[Unit]("")
val checkSrcJarRestored = taskKey[Unit]("")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

lazy val taskCacheStores = project
  .in(file("."))
  .settings(
    scalaVersion := "3.9.0",
    usePipelining := true,
    Compile / compile / cacheStores := Nil,
  )

def markMillis(f: File): Long = IO.read(f).trim.toLong / 1000 * 1000

mark := baseDirectory.value / "mark.txt"

markTime := Def.uncached(IO.write(mark.value, System.currentTimeMillis.toString))

delClasses := Def.uncached(IO.delete((Compile / classDirectory).value))

delSrcJar := Def.uncached {
  val c = fileConverter.value
  IO.delete(c.toPath((Compile / packageSrc / artifactPath).value).toFile)
}

checkClassesRecompiled := Def.uncached {
  val since = markMillis(mark.value)
  val classes = ((Compile / classDirectory).value ** "*.class").get()
  assert(classes.nonEmpty, "no class files")
  val restored = classes.filter(_.lastModified < since)
  assert(restored.isEmpty, s"restored from the cache: ${restored.mkString(", ")}")
}

checkSrcJarRestored := Def.uncached {
  val since = markMillis(mark.value)
  val jar = fileConverter.value.toPath((Compile / packageSrc / artifactPath).value).toFile
  assert(jar.lastModified < since, s"$jar was rebuilt instead of restored from the cache")
}
