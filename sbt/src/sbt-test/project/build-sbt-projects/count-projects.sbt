InputKey[Unit]("count-projects") <<= inputTask { (argTask: TaskKey[Seq[String]]) =>
  (argTask, projects) map { (args: Seq[String], p: Seq[ResolvedProject]) =>
    assert(args.length == 1)
    println("-" * 174)
    p foreach println
    println("-" * 174)
    if (p.length != args.head.toInt) error("Expected " + args.head + " projects, but counted " + p.length)
  }
}
