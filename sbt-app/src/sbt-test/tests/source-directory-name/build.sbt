import sbt.io.Using

TaskKey[Unit]("check") := {
  val p = (Compile / packageBin).value
  val c = fileConverter.value
  Using.jarFile(false)(c.toPath(p).toFile()): jar =>
    assert(jar.getJarEntry("ch/epfl/scala/Client.class") ne null)
}
