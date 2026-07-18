Global / localCacheDirectory := baseDirectory.value / "diskcache"
scalaVersion := "3.8.4"

val denyClassDir = taskKey[Unit]("Remove write permission from the compile output dir")
denyClassDir := {
  val dir = (Compile / classDirectory).value
  IO.createDirectory(dir)
  assert(dir.setWritable(false, false), s"could not clear write permission on $dir")
}

val allowClassDir = taskKey[Unit]("Restore write permission on the compile output dir")
allowClassDir := {
  val dir = (Compile / classDirectory).value
  assert(dir.setWritable(true, false), s"could not restore write permission on $dir")
}
