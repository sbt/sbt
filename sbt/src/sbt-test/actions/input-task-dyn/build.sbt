import complete.Parser

// http://www.scala-sbt.org/0.13/docs/Input-Tasks.html

val runFoo = inputKey[Unit]("Runs Foo with passed arguments")
val check = taskKey[Unit]("")

lazy val root = (project in file(".")).
  settings(
    name := "run-test",
    runFoo := Def.inputTaskDyn {
        val args = Def.spaceDelimited().parsed
        (runMain in Compile).toTask(s" Foo " + args.mkString(" "))
    }.evaluated,
    check := {
      val x = runFoo.toTask(" hi ho").value
    }
  )
