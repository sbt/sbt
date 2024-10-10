lazy val undefinedIntTask = taskKey[Int]("")
lazy val intTask1 = taskKey[Int]("")
lazy val orResultTask = taskKey[Int]("")
lazy val checkOption = taskKey[Unit]("")
lazy val checkGetOrElse = taskKey[Unit]("")
lazy val checkOr = taskKey[Unit]("")

intTask1 := 1

checkOption := {
  val x = undefinedIntTask.option.value
  assert(x == None)
}

checkGetOrElse := {
  val x = undefinedIntTask.getOrElse(10).value
  assert(x == 10)
}

orResultTask := (undefinedIntTask or intTask1).value
checkOr := {
  val x = orResultTask.value
  assert(x == 1)
}
