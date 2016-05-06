TaskKey[Unit]("checkJavaFailures") := {
  val reporter = savedReporter.value
  val ignore = (compile in Compile).failure.value
  val ps = reporter.problems
  assert(!ps.isEmpty, "Failed to report any problems!")
  // First error should be on a specific line/file
  val first = ps(0)
  assert(first.position.line.get == 3, s"First failure position is not line 3, failure = $first")
  val javaFile = baseDirectory.value / "src/main/java/bad.java"
  val file = new File(first.position.sourcePath.get)
  assert(file == javaFile, s"First failure file location is not $javaFile, $first")
}

TaskKey[Unit]("checkScalaFailures") := {
  val reporter = savedReporter.value
  val ignore = (compile in Compile).failure.value
  val ps = reporter.problems
  assert(!ps.isEmpty, "Failed to report any problems!")
  // First error should be on a specific line/file
  val first = ps(0)
  assert(first.position.line.get == 2, s"First failure position is not line 2, failure = $first")
  val scalaFile = baseDirectory.value / "src/main/scala/bad.scala"
  val file = new File(first.position.sourcePath.get)
  assert(file == scalaFile, s"First failure file location is not $scalaFile, $first")
}

compileOrder := CompileOrder.Mixed
