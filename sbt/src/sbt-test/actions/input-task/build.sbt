import complete.Parser

// http://www.scala-sbt.org/0.13/docs/Input-Tasks.html

val run2 = inputKey[Unit](
    "Runs the main class twice with different argument lists separated by --")
val check = taskKey[Unit]("")

val separator: Parser[String] = "--"

lazy val root = (project in file(".")).
  settings(
    name := "run-test",
    run2 := {
       val one = (run in Compile).evaluated
       val sep = separator.parsed
       val two = (run in Compile).evaluated
    },
    check := {
      val x = run2.toTask(" a b -- c d").value
    }
  )
