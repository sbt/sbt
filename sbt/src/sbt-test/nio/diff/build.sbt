import sbt.nio.Keys._

val fileInputTask = taskKey[Unit]("task with file inputs")

fileInputTask / fileInputs += Glob(baseDirectory.value / "base", "*.md")

fileInputTask := {
  if (fileInputTask.changedInputFiles.fold(false)(
        _.created.exists(_.getFileName.toString.startsWith("foo"))
      )) assert(false)
  assert(true)
}
