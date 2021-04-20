import java.nio.file.Files

TaskKey[Unit]("createSymlinkedDirectory") := {
  Files.createSymbolicLink(target.value.toPath / "foo", baseDirectory.value.toPath / "foo")
}
