import java.io.File

@transient
val myFileTask = taskKey[File]("task that returns File")
val badFileTask = taskKey[File]("task without @transient that should fail to cache")
val checkFileTask = taskKey[Unit]("verifies file task returns correct value")

myFileTask := {
  new File(scalaVersion.value)
}

checkFileTask := {
  val f = myFileTask.value
  val expected = new File(scalaVersion.value)
  assert(f == expected, s"Expected $expected but got $f")
}
