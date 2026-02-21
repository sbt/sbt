@transient
val taskA = taskKey[File]("")
@transient
val taskB = taskKey[File]("")

@transient
val taskE = taskKey[File]("")
@transient
val taskF = taskKey[File]("")

scalaVersion := "3.3.1"
name := "task-map"
taskA := {
  touch(target.value / "a")
  target.value / "a"
}

taskB := {
  touch(target.value / "b")
  target.value / "b"
}

taskE := {
  touch(target.value / "e")
  target.value / "e"
}

taskF := {
  touch(target.value / "f")
  target.value / "f"
}

//   a <<= a triggeredBy b
// means "a" will be triggered by "b"
// said differently, invoking "b" will run "b" and then run "a"

taskA := taskA.triggeredBy(taskB).value

//   e <<= e runBefore f
// means "e" will be run before running "f"
// said differently, invoking "f" will run "e" and then run "f"

taskE := taskE.runBefore(taskF).value

// test utils
def touch(f: File): File = { IO.touch(f); f }
