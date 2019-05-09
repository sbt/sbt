val checkCount = inputKey[Unit]("check that compile has run a specified number of times")
val failingTask = taskKey[Unit]("should always fail")
val resetCount = taskKey[Unit]("reset compile count")

checkCount := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  if (Count.get != expected)
    throw new IllegalStateException(s"Expected ${expected} compilation runs, got ${Count.get}")
}

resetCount := {
  Count.reset()
}

failingTask := {
  throw new IllegalStateException("failed")
}

Compile / compile := {
  Count.increment()
  // Trigger a new build by updating the last modified time
  val file = (Compile / scalaSource).value / "A.scala"
  IO.write(file, IO.read(file) + ("\n" * Count.get))
  (Compile / compile).value
}
