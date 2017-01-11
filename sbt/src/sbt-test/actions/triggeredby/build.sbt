val taskA = taskKey[File]("")
val taskB = taskKey[File]("")
val taskC = taskKey[File]("")
val taskD = taskKey[File]("")

taskA := touch(target.value / "a")
taskB := touch(target.value / "b")
taskC := touch(target.value / "c")
taskD := touch(target.value / "d")

//   a <<= a triggeredBy b
// means "a" will be triggered by "b"
// said differently, invoking "b" will run "b" and then run "a"

taskA <<= taskA triggeredBy taskB
taskC := (taskC triggeredBy taskD).value



// test utils
def touch(f: File): File = { IO touch f; f }
