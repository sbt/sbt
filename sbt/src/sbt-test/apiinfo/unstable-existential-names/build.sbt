import sbt.internal.inc.Analysis

// checks number of compilation iterations performed since last `clean` run
InputKey[Unit]("check-number-of-compiler-iterations") := {
  val args = Def.spaceDelimited().parsed
  val a = (compile in Compile).value.asInstanceOf[Analysis]
  assert(args.size == 1)
  val expectedIterationsNumber = args(0).toInt
  val allCompilationsSize = a.compilations.allCompilations.size
  assert(allCompilationsSize == expectedIterationsNumber,
    "allCompilationsSize == %d (expected %d)".format(allCompilationsSize, expectedIterationsNumber))
}
