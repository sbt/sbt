// Check that a file has not been recompiled during last compilation
InputKey[Unit]("check-not-recompiled") <<= inputTask { (argTask: TaskKey[Seq[String]]) =>
    (argTask, compile in Compile) map { case (args: Seq[String], a: Analysis) =>
        assert(args.size == 1)
        val fileCompilation = a.apis.internal.collect { case (file, src) if file.name.endsWith(args(0)) => src.compilation }.head
        val lastCompilation = a.compilations.allCompilations.last
        assert(fileCompilation.startTime != lastCompilation.startTime, "File has been recompiled during last compilation.")
    }
}