val taskA = taskKey[File]("")
val taskB = taskKey[File]("")

val taskE = taskKey[File]("")
val taskF = taskKey[File]("")

taskA := touch(target.value / "a")
taskB := touch(target.value / "b")

taskE := touch(target.value / "e")
taskF := touch(target.value / "f")

//   a <<= a triggeredBy b
// means "a" will be triggered by "b"
// said differently, invoking "b" will run "b" and then run "a"

taskA := (taskA triggeredBy taskB).value

//   e <<= e runBefore f
// means "e" will be run before running "f"
// said differently, invoking "f" will run "e" and then run "f"

taskE := (taskE runBefore taskF).value

// test utils
def touch(f: File): File = { IO touch f; f }
