val taskA = taskKey[File]("")
val taskB = taskKey[File]("")
val taskC = taskKey[File]("")
val taskD = taskKey[File]("")

val taskE = taskKey[File]("")
val taskF = taskKey[File]("")
val taskG = taskKey[File]("")
val taskH = taskKey[File]("")

taskA := touch(target.value / "a")
taskB := touch(target.value / "b")
taskC := touch(target.value / "c")
taskD := touch(target.value / "d")

taskE := touch(target.value / "e")
taskF := touch(target.value / "f")
taskG := touch(target.value / "g")
taskH := touch(target.value / "h")

//   a <<= a triggeredBy b
// means "a" will be triggered by "b"
// said differently, invoking "b" will run "b" and then run "a"

taskA <<= taskA triggeredBy taskB
taskC := (taskC triggeredBy taskD).value

//   e <<= e runBefore f
// means "e" will be run before running "f"
// said differently, invoking "f" will run "e" and then run "f"

taskE <<= taskE runBefore taskF
taskG := (taskG runBefore taskH).value



// test utils
def touch(f: File): File = { IO touch f; f }
