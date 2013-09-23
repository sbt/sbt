// checks number of compilation iterations performed since last `clean` run
InputKey[Unit]("check-number-of-compiler-iterations") <<= inputTask { (argTask: TaskKey[Seq[String]]) =>
  (argTask, compile in Compile) map { (args: Seq[String], a: sbt.inc.Analysis) =>
    assert(args.size == 1)
    val expectedIterationsNumber = args(0).toInt
    val allCompilationsSize = a.compilations.allCompilations.size
    assert(allCompilationsSize == expectedIterationsNumber,
      "allCompilationsSize == %d (expected %d)".format(allCompilationsSize, expectedIterationsNumber))
  }
}
