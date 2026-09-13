import java.nio.file.Files

name := "clean-symlinks-test"

scalaVersion := "3.9.0"

TaskKey[Unit]("createSymlinkedDirectory") := {
  IO.createDirectory(target.value)
  Files.createSymbolicLink(target.value.toPath / "foo", baseDirectory.value.toPath / "foo")
}
