val condition = taskKey[Boolean]("")
val number = settingKey[Int]("")
val trueAction = taskKey[Unit]("")
val falseAction = taskKey[Unit]("")
val negAction = taskKey[Unit]("")
val zeroAction = taskKey[Unit]("")
val posAction = taskKey[Unit]("")
val foo = taskKey[Unit]("")
val bar = taskKey[Unit]("")
val output = settingKey[File]("")

lazy val root = (project in file("."))
  .settings(
    name := "conditional",
    output := baseDirectory.value / "output.txt",
    condition := true,
    number := -1,

    // automatic conversion to Def.ifS
    bar := {
      if (number.value < 0) negAction.value
      else if (number.value == 0) zeroAction.value
      else posAction.value
    },

    // explicit call to Def.taskIf
    foo := (Def.taskIf {
      if (condition.value) trueAction.value
      else falseAction.value
    }).value,

    trueAction := { IO.write(output.value, s"true\n", append = true) },
    falseAction := { IO.write(output.value, s"false\n", append = true) },
    negAction := { IO.write(output.value, s"neg\n", append = true) },
    zeroAction := { IO.write(output.value, s"zero\n", append = true) },
    posAction := { IO.write(output.value, s"pos\n", append = true) },

    TaskKey[Unit]("checkTrue") := checkLines("true"),
    TaskKey[Unit]("checkFalse") := checkLines("false"),
    TaskKey[Unit]("checkNeg") := checkLines("neg"),
    TaskKey[Unit]("checkZero") := checkLines("zero"),

    // https://github.com/sbt/sbt/issues/5625
    javacOptions ++= (
      if (true) Seq.empty
      else Seq("--release", "8")
    ),
  )

def checkLines(content: String) = Def.task {
  val lines = IO.read(output.value).linesIterator.toList
  assert(lines == List("true"), s"$content was expected but found: $lines")
  ()
}
