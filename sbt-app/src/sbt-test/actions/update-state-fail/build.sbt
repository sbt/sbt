lazy val akey = AttributeKey[Int]("testKey")
lazy val testTask = taskKey[String]("")
lazy val check = inputKey[Unit]("")

lazy val root = (project in file(".")).
  aggregate(a, b).
  settings(
    check := checkState(checkParser.parsed, state.value)
  )

lazy val a = project.
  settings(
    testTask := sys.error("Failing")
  )

lazy val b = project.
  settings(
    testTask := Def.task("").updateState(updater).value
  )

def checkState(runs: Int, s: State): Unit = {
  val stored = s.get(akey).getOrElse(0)
  assert(stored == runs, "Expected " + runs + ", got " + stored)
}

def updater(s: State, a: AnyRef): State = s.update(akey)(_.getOrElse(0) + 1)

import complete.DefaultParsers._

lazy val checkParser = token(Space ~> IntBasic)
