import sbt.nio.Keys._
import sbt.internal.FileChangesMacro._

val testTask = taskKey[Unit]("test task")
val otherTask = taskKey[Unit]("dummy task")

otherTask / testTask / fileInputs := Seq(
  baseDirectory.value.toGlob / "src" / "*.txt"
)

val checkChanges = taskKey[Unit]("check that file changes are detected")
checkChanges := Def.uncached {
  val changes = (otherTask / testTask).inputFileChanges
  val files = (otherTask / testTask).inputFiles
  assert(files.nonEmpty, "inputFiles should not be empty")
}

val checkModified = taskKey[Unit]("check that modified files are detected")
checkModified := Def.uncached {
  val changes = (otherTask / testTask).inputFileChanges
  if (changes.modified.nonEmpty) {
    assert(changes.modified.exists(_.getFileName.toString == "test.txt"))
  }
}

val checkCreated = taskKey[Unit]("check that created files are detected")
checkCreated := Def.uncached {
  val changes = (otherTask / testTask).inputFileChanges
  if (changes.created.nonEmpty && changes.unmodified.nonEmpty) {
    assert(changes.created.exists(_.getFileName.toString == "new.txt"))
  }
}
