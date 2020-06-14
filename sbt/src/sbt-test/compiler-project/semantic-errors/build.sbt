TaskKey[Unit]("checkJavaFailures") := {
  val reporter = savedReporter.value
  val ignore = (compile in Compile).failure.value
  val ps = reporter.problems
  assert(!ps.isEmpty, "Failed to report any problems!")
  // First error should be on a specific line/file
  val first = ps(0)
  assert(first.position.line.get == 3, s"First failure position is not line 3, failure = $first")
  val expected = "${BASE}/src/main/java/bad.java"
  val sourcePath = first.position.sourcePath.get
  assert(sourcePath == expected, s"$sourcePath == $expected was false")
}

TaskKey[Unit]("checkScalaFailures") := {
  val reporter = savedReporter.value
  val ignore = (compile in Compile).failure.value
  val ps = reporter.problems
  assert(!ps.isEmpty, "Failed to report any problems!")
  // First error should be on a specific line/file
  val first = ps(0)
  assert(first.position.line.get == 2, s"First failure position is not line 2, failure = $first")
  val expected = "${BASE}/src/main/scala/bad.scala"
  val sourcePath = first.position.sourcePath.get
  assert(sourcePath == expected, s"$sourcePath == $expected was false")
}
