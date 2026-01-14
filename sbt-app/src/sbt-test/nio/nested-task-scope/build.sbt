import sbt.nio.Keys._
import sbt.internal.FileChangesMacro._

val testTask = taskKey[Unit]("test task")
val otherTask = taskKey[Unit]("dummy task")

otherTask / testTask / fileInputs := Seq(
  baseDirectory.value.toGlob / "src" / "*.txt"
)

// Test that inputFileChanges works with nested task scopes (fixes #7489)
val checkChanges = taskKey[Unit]("check that file changes are detected")
checkChanges := Def.taskDyn {
  val files = (otherTask / testTask).inputFiles
  val changes = (otherTask / testTask).inputFileChanges
  Def.task {
    assert(files.nonEmpty, "inputFiles should not be empty")
  }
}.value
