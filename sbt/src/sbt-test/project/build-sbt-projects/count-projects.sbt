InputKey[Unit]("count-projects") <<= inputTask { (argTask: TaskKey[Seq[String]]) =>
  argTask map { args =>
    assert(args.length == 1)
    if (projects.length != args.head.toInt) error("Expected " + args.head + " projects, but counted " + projects.length)
  }
}
