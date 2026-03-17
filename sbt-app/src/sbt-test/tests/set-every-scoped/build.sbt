val a = project
val b = project

val checkCompileSourcesNonEmpty = taskKey[Unit]("Verify Compile / sources is still non-empty")

checkCompileSourcesNonEmpty := {
  val srcs = (Compile / sources).value
  if (srcs.isEmpty)
    sys.error("Compile / sources should not be empty, but it was.")
}

val checkTestSourcesEmpty = taskKey[Unit]("Verify Test / sources is empty")

checkTestSourcesEmpty := {
  val srcs = (Test / sources).value
  if (srcs.nonEmpty)
    sys.error(s"Test / sources should be empty, but had: ${srcs}")
}
