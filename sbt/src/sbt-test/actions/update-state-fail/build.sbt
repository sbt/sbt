lazy val akey = AttributeKey[Int]("TestKey")
lazy val t = TaskKey[String]("test-task")
lazy val check = InputKey[Unit]("check")

lazy val root = (project in file(".")).
  aggregate(a, b).
  settings(
    check := checkState(checkParser.parsed, state.value)
  )

lazy val a = project.
  settings(
    t := sys.error("Failing")
  )

lazy val b = project.
  settings(
    t <<= Def.task("").updateState(updater)
  )

def checkState(runs: Int, s: State): Unit = {
  val stored = s.get(akey).getOrElse(0)
  assert(stored == runs, "Expected " + runs + ", got " + stored)
}

def updater(s: State, a: AnyRef): State = s.update(akey)(_.getOrElse(0) + 1)

import complete.DefaultParsers._

lazy val checkParser = token(Space ~> IntBasic)
