val foo = taskKey[Unit]("dummy task with inputs")
foo / fileInputs += baseDirectory.value.toGlob / "foo" / *

val bar = taskKey[Unit]("dummy task with inputs")
bar / fileInputs += baseDirectory.value.toGlob / "bar" / *

val check = taskKey[Unit]("check expected changes")
check := {
  foo.changedInputFiles.toSeq.flatMap(_.updated) ++
    bar.changedInputFiles.toSeq.flatMap(_.updated) match {
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
