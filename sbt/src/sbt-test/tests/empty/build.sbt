testGrouping := {
  val tests = (definedTests in Test).value
  tests map { test =>
    new Tests.Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = Tests.SubProcess(ForkOptions(runJVMOptions = Seq.empty[String]))
    )
  }
}
