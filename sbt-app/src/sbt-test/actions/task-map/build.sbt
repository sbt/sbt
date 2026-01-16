val taskA = taskKey[File]("")
val taskB = taskKey[File]("")

val taskE = taskKey[File]("")
val taskF = taskKey[File]("")

scalaVersion := "3.3.1"
name := "task-map"
taskA := {
  val c = fileConverter.value
  touch(target.value / "a")
  Def.declareOutput(c.toVirtualFile((target.value / "a").toPath()))
  target.value / "a"
}

taskB := {
  val c = fileConverter.value
  touch(target.value / "b")
  Def.declareOutput(c.toVirtualFile((target.value / "b").toPath()))
  target.value / "b"
}

taskE := {
  val c = fileConverter.value
  touch(target.value / "e")
  Def.declareOutput(c.toVirtualFile((target.value / "e").toPath()))
  target.value / "e"
}

taskF := {
  val c = fileConverter.value
  touch(target.value / "f")
  Def.declareOutput(c.toVirtualFile((target.value / "f").toPath()))
  target.value / "f"
}

//   a <<= a triggeredBy b
// means "a" will be triggered by "b"
// said differently, invoking "b" will run "b" and then run "a"

taskA := Def.uncached(taskA.triggeredBy(taskB).value)

//   e <<= e runBefore f
// means "e" will be run before running "f"
// said differently, invoking "f" will run "e" and then run "f"

taskE := Def.uncached(taskE.runBefore(taskF).value)

// test utils
def touch(f: File): File = { IO.touch(f); f }
