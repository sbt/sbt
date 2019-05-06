import sbt.nio.Keys._

val foo = taskKey[Unit]("foo")
foo / fileInputs := Seq(
  baseDirectory.value.toGlob / "base" / "*.md",
  baseDirectory.value.toGlob / "base" / "*.txt",
)

val checkModified = taskKey[Unit]("check that modified files are returned")
checkModified := Def.taskDyn {
  val modified = (foo / changedInputFiles).value.map(_.updated).getOrElse(Nil)
  val allFiles = (foo / allInputFiles).value
  if (modified.isEmpty) Def.task(assert(true))
  else Def.task {
    assert(modified != allFiles)
    assert(modified == Seq((baseDirectory.value / "base" / "Bar.md").toPath))
  }
}.value

val checkRemoved = taskKey[Unit]("check that removed files are returned")
checkRemoved := Def.taskDyn {
  val files = (foo / allInputFiles).value
  val removed = (foo / changedInputFiles).value.map(_.deleted).getOrElse(Nil)
  if (removed.isEmpty) Def.task(assert(true))
  else Def.task {
    assert(files == Seq((baseDirectory.value / "base" / "Foo.txt").toPath))
    assert(removed == Seq((baseDirectory.value / "base" / "Bar.md").toPath))
  }
}.value

val checkAdded = taskKey[Unit]("check that modified files are returned")
checkAdded := Def.taskDyn {
  val files = (foo / allInputFiles).value
  val added = (foo / changedInputFiles).value.map(_.created).getOrElse(Nil)
  if (added.isEmpty || files.sameElements(added)) Def.task(assert(true))
  else Def.task {
    val base = baseDirectory.value / "base"
    assert(files.sameElements(Seq("Bar.md", "Foo.txt").map(p => (base / p).toPath)))
    assert(added == Seq((baseDirectory.value / "base" / "Bar.md").toPath))
  }
}.value
