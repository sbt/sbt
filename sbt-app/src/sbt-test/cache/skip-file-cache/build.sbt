import java.io.File

val myFileTask = taskKey[File]("task that returns File")
val checkFileTask = taskKey[Unit]("verifies file task returns correct value")

myFileTask := Def.uncached {
  new File(scalaVersion.value)
}

checkFileTask := Def.uncached {
  val f = myFileTask.value
  val expected = new File(scalaVersion.value)
  assert(f == expected, s"Expected $expected but got $f")
}
