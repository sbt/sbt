// verifies if recompilation of `Bar.scala` was triggered by the change of
// the return type of `Foo.foo` (defined in `Foo.scala`)
TaskKey[Unit]("verify-recompiled") <<= (compile in Compile, scalaSource in Compile) map { (a: sbt.inc.Analysis, scalaSrc: java.io.File) =>
  val fooApi = a.apis.internalAPI(scalaSrc / "Foo.scala")
  val barApi = a.apis.internalAPI(scalaSrc / "Bar.scala")
  // check if `Bar.scala` was compiled at the same time or after `Foo.scala`
  assert(barApi.compilation.startTime >= fooApi.compilation.startTime)
}
