import sbt.nio.file.Glob

lazy val previousCompileIsEmpty = taskKey[Unit]("")
lazy val previousCompileIsNonEmpty = taskKey[Unit]("")

previousCompileIsEmpty := {
  val previous = (Compile / previousCompile).value
  assert(previous.analysis.isEmpty())
  assert(previous.setup.isEmpty())
}

previousCompileIsNonEmpty := {
  val previous = (Compile / previousCompile).value
  assert(!previous.analysis.isEmpty())
  assert(!previous.setup.isEmpty())
}
