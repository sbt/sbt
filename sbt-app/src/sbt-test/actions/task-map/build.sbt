val taskA = taskKey[File]("")
val taskB = taskKey[File]("")

val taskE = taskKey[File]("")
val taskF = taskKey[File]("")

scalaVersion := "3.3.1"
name := "task-map"
taskA := Def.uncached {
  touch(target.value / "a")
  target.value / "a"
}

taskB := Def.uncached {
  touch(target.value / "b")
  target.value / "b"
}

taskE := Def.uncached {
  touch(target.value / "e")
  target.value / "e"
}

taskF := Def.uncached {
  touch(target.value / "f")
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
