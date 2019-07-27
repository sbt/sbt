import java.nio.file.Path

val foo = taskKey[Seq[Path]]("dummy task with inputs")
foo := fileTreeView.value.list(baseDirectory.value.toGlob / "foo" / *).map(_._1)

val bar = taskKey[Seq[Path]]("dummy task with inputs")
bar := fileTreeView.value.list(baseDirectory.value.toGlob / "bar" / *).map(_._1)

val check = taskKey[Unit]("check expected changes")
check := {
  foo.outputFileChanges.modified ++ bar.outputFileChanges.modified match {
    case Nil =>
      val contents = IO.read(baseDirectory.value / "foo" / "foo.md")
      assert(contents == "foo", s"expected 'foo', got '$contents")
    case Seq(f, b) =>
      val fContents = IO.read(f.toFile)
      assert(fContents == "updated", s"expected 'updated', got '$fContents' for $f")
      val bContents = IO.read(b.toFile)
      assert(bContents == "updated", s"expected 'updated', got '$fContents' for $b")
  }
}

val setModified = inputKey[Unit]("set the last modified time for a file")
setModified := {
  val Seq(relative, lm) = Def.spaceDelimited().parsed
  // be safe in case of windows
  val file = relative.split("/") match {
    case Array(h, rest @ _*) => rest.foldLeft(baseDirectory.value / h)(_ / _)
  }
  IO.setModifiedTimeOrFalse(file, lm.toLong)
}
