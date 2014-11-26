// Check that a file has been recompiled during last compilation
InputKey[Unit]("check-recompiled") <<= inputTask { (argTask: TaskKey[Seq[String]]) =>
    (argTask, compile in Compile) map { (args: Seq[String], a: sbt.inc.Analysis) =>
        assert(args.size == 1)
        val fileCompilation = a.apis.internal.collect { case (file, src) if file.name.endsWith(args(0)) => src.compilation }.head
        val lastCompilation = a.compilations.allCompilations.last
        assert(fileCompilation.startTime == lastCompilation.startTime, "File has not been recompiled during last compilation.")
    }
}