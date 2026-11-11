Global / localCacheDirectory := baseDirectory.value / "diskcache"
crossPaths := false
autoScalaLibrary := false

val blockPackageDir = taskKey[Unit]("Put a regular file where the p package dir must go")
blockPackageDir := Def.uncached {
  val dir = (Compile / classDirectory).value
  IO.createDirectory(dir)
  IO.write(dir / "p", "blocker")
}

val unblockPackageDir = taskKey[Unit]("Remove the blocker file")
unblockPackageDir := Def.uncached {
  IO.delete((Compile / classDirectory).value / "p")
}
