import sbt.nio.Keys._
import sbt.internal.FileChangesMacro._

val testTask = taskKey[Unit]("test task")
val otherTask = taskKey[Unit]("dummy task")

// Set fileInputs at nested task scope: otherTask / testTask
otherTask / testTask / fileInputs := Seq(
  baseDirectory.value.toGlob / "src" / "*.txt"
)

// Create a scoped task key to use with the inputFileChanges macro
val scopedTestTask = otherTask / testTask

// Test that inputFileChanges works with nested task scopes (fixes #7489)
val checkChanges = taskKey[Unit]("check that file changes are detected")
checkChanges := {
  val files = scopedTestTask.inputFiles
  assert(files.nonEmpty, s"inputFiles should not be empty, got: $files")
}
