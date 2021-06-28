import java.nio.file.Path

val foo = taskKey[Path]("foo")
// Check a direct override
foo / outputFileStamps := Nil
foo := baseDirectory.value.toPath / "foo.txt"

TaskKey[Unit]("checkFoo") := assert((foo / outputFileStamps).value == Nil)

val bar = taskKey[Path]("bar")
// Check an append
bar / outputFileStamps ++= (baz / outputFileStamps).value
bar / outputFileStamps ++= (baz / outputFileStamps).value
bar := baseDirectory.value.toPath / "bar.txt"

val baz = taskKey[Path]("baz")
baz := baseDirectory.value.toPath / "baz.txt"

TaskKey[Unit]("checkBar") := {
  val stamps = (bar / outputFileStamps).value
  assert(stamps.length == 3)
  val fileNames = stamps.map(_._1.getFileName.toString).toSet
  assert(fileNames == Set("bar.txt", "baz.txt"))
}
