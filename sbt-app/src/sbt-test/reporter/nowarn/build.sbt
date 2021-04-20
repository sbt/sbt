lazy val sub1 = project

lazy val sub2 = project

val assertNoWarning = taskKey[Unit]("checks warning *is not* emitted")

val assertWarning = taskKey[Unit]("checks warning *is* emitted")

def check(expectation: Boolean) = Def.task[Unit] {
  val lastLog: File = BuiltinCommands.lastLogFile(state.value).get
  val last: String = IO.read(lastLog)
  val contains = last.contains("a pure expression does nothing in statement position")
  if (expectation && !contains)
    sys.error("compiler output does not contain warning")
  else if (!expectation && contains)
    sys.error(s"compiler output still contains warning")
  else {
    IO.write(lastLog, "") // clear the backing log for for 'last'.
  }
}

assertWarning := check(true).value

assertNoWarning := check(false).value
