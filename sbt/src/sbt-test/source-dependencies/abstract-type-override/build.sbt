import sbt.internal.inc.Analysis

InputKey[Unit]("checkNumberOfCompilerIterations") := {
  val a = (compile in Compile).value.asInstanceOf[Analysis]
  val args = Def.spaceDelimited().parsed
  assert(args.size == 1)
  val expectedIterationsNumber = args(0).toInt
  assert(a.compilations.allCompilations.size == expectedIterationsNumber,
    "a.compilations.allCompilations.size = %d (expected %d)".format(
      a.compilations.allCompilations.size, expectedIterationsNumber)
  )
}
