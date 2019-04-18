import sbt.nio.Keys._

val fileInputTask = taskKey[Unit]("task with file inputs")

fileInputTask / fileInputs += Glob(baseDirectory.value / "base", "*.md")

fileInputTask := Def.taskDyn {
  if ((fileInputTask / changedFiles).value.nonEmpty) Def.task(assert(true))
  else Def.task(assert(false))
}.value
