import java.nio.file.Files

scalaVersion := "3.9.0"

Global / localCacheDirectory := baseDirectory.value / "diskcache"

@transient
val createSymlinkLoop = taskKey[Unit]("create a self-referencing symlink under the base directory")
createSymlinkLoop := {
  val dir = Files.createDirectories(baseDirectory.value.toPath / "flake-inputs")
  val link = dir / "self"
  Files.deleteIfExists(link)
  Files.createSymbolicLink(link, dir)
  assert(Files.isSameFile(link, dir), s"$link does not point back to $dir")
}

commands += Command.single("skipWindows") { (state, command) =>
  if (scala.util.Properties.isWin) state else command :: state
}
