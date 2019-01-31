import sbt.internal.TransitiveGlobs._
val cached = settingKey[Unit]("")
val newInputs = settingKey[Unit]("")
Compile / cached / fileInputs := (Compile / unmanagedSources / fileInputs).value ++
  (Compile / unmanagedResources / fileInputs).value
Test / cached / fileInputs := (Test / unmanagedSources / fileInputs).value ++
  (Test / unmanagedResources / fileInputs).value
Compile / newInputs / fileInputs += baseDirectory.value * "*.sc"

Compile / unmanagedSources / fileInputs ++= (Compile / newInputs / fileInputs).value

val checkCompile = taskKey[Unit]("check compile inputs")
checkCompile := {
  val actual = (Compile / compile / transitiveInputs).value.toSet
  val expected = ((Compile / cached / fileInputs).value ++ (Compile / newInputs / fileInputs).value).toSet
  streams.value.log.debug(s"actual: $actual\nexpected:$expected")
  if (actual != expected) {
    val actualExtra = actual diff expected
    val expectedExtra = expected diff actual
    throw new IllegalStateException(
      s"$actual did not equal $expected\n" +
        s"${if (actualExtra.nonEmpty) s"Actual result had extra fields $actualExtra" else ""}" +
        s"${if (expectedExtra.nonEmpty) s"Actual result was missing: $expectedExtra" else ""}")
  }

}

val checkRun = taskKey[Unit]("check runtime inputs")
checkRun := {
  val actual = (Runtime / run / transitiveInputs).value.toSet
  // Runtime doesn't add any new inputs, but it should correctly find the Compile inputs via
  // delegation.
  val expected = ((Compile / cached / fileInputs).value ++ (Compile / newInputs / fileInputs).value).toSet
  streams.value.log.debug(s"actual: $actual\nexpected:$expected")
  if (actual != expected) {
    val actualExtra = actual diff expected
    val expectedExtra = expected diff actual
    throw new IllegalStateException(
        s"${if (actualExtra.nonEmpty) s"Actual result had extra fields: $actualExtra" else ""}" +
        s"${if (expectedExtra.nonEmpty) s"Actual result was missing: $expectedExtra" else ""}")
  }
}

val checkTest = taskKey[Unit]("check test inputs")
checkTest := {
  val actual = (Test / compile / transitiveInputs).value.toSet
  val expected = ((Test / cached / fileInputs).value ++ (Compile / newInputs / fileInputs).value ++
    (Compile / cached / fileInputs).value).toSet
  streams.value.log.debug(s"actual: $actual\nexpected:$expected")
  if (actual != expected) {
    val actualExtra = actual diff expected
    val expectedExtra = expected diff actual
    throw new IllegalStateException(
      s"$actual did not equal $expected\n" +
        s"${if (actualExtra.nonEmpty) s"Actual result had extra fields $actualExtra" else ""}" +
        s"${if (expectedExtra.nonEmpty) s"Actual result was missing: $expectedExtra" else ""}")
  }
}
