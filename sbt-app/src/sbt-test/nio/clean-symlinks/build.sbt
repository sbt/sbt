import java.nio.file.Files

name := "clean-symlinks-test"

scalaVersion := "3.7.3"

TaskKey[Unit]("createSymlinkedDirectory") := {
  IO.createDirectory(target.value)
  Files.createSymbolicLink(target.value.toPath / "foo", baseDirectory.value.toPath / "foo")
}
