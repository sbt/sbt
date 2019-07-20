import sbt.nio.Keys._

val foo = taskKey[Unit]("foo")
foo / fileInputs := Seq(
  baseDirectory.value.toGlob / "base" / "*.md",
  baseDirectory.value.toGlob / "base" / "*.txt",
)

val checkModified = taskKey[Unit]("check that modified files are returned")
checkModified := {
  val changes = foo.changedInputFiles
  val modified = changes.map(_.updated).getOrElse(Nil)
  println(modified)
  val allFiles = (foo / allInputFiles).value
  if (modified.isEmpty) assert(true)
  else {
    assert(modified != allFiles)
    assert(modified == Seq((baseDirectory.value / "base" / "Bar.md").toPath))
  }
}

val checkRemoved = taskKey[Unit]("check that removed files are returned")
checkRemoved := Def.taskDyn {
  val files = (foo / allInputFiles).value
  val removed = foo.changedInputFiles.map(_.deleted).getOrElse(Nil)
  if (removed.isEmpty) Def.task(assert(true))
  else Def.task {
    assert(files == Seq((baseDirectory.value / "base" / "Foo.txt").toPath))
    assert(removed == Seq((baseDirectory.value / "base" / "Bar.md").toPath))
  }
}.value

val checkAdded = taskKey[Unit]("check that modified files are returned")
checkAdded := Def.taskDyn {
  val files = (foo / allInputFiles).value
  val added = foo.changedInputFiles.map(_.created).getOrElse(Nil)
  if (added.isEmpty || (files.toSet == added.toSet)) Def.task(assert(true))
  else Def.task {
    val base = baseDirectory.value / "base"
    assert(files.toSet == Set("Bar.md", "Foo.txt").map(p => (base / p).toPath))
    assert(added == Seq((baseDirectory.value / "base" / "Bar.md").toPath))
  }
}.value
