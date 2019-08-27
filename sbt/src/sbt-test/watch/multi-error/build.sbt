import java.nio.file._
watchOnIteration := { (_, _, _) => Watch.CancelWatch }

val copyFile = taskKey[Unit]("copy a file")
copyFile := {
  val base = baseDirectory.value.toPath
  Files.copy(base / "changes" / "Foo.txt", base / "Foo.txt")
}