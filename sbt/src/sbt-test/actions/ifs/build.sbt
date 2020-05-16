val condition = taskKey[Boolean]("")
val trueAction = taskKey[Unit]("")
val falseAction = taskKey[Unit]("")
val foo = taskKey[Unit]("")
val output = settingKey[File]("")

lazy val root = (project in file("."))
  .settings(
    name := "ifs",
    output := baseDirectory.value / "output.txt",
    condition := true,
    trueAction := { IO.write(output.value, s"true\n", append = true) },
    falseAction := { IO.write(output.value, s"false\n", append = true) },
    foo := (Def.taskIf {
      if (condition.value) trueAction
      else falseAction
    }).value,
    TaskKey[Unit]("check") := {
      val lines = IO.read(output.value).linesIterator.toList
      assert(lines == List("true"))
      ()
    },
    TaskKey[Unit]("check2") := {
      val lines = IO.read(output.value).linesIterator.toList
      assert(lines == List("false"))
      ()
    },
  )
