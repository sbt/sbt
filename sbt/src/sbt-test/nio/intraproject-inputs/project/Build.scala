package sbt
package interproject.inputs

import sbt.Keys._
import sbt.nio.Keys._

/**
* This test is for internal logic so it must be in the sbt package because it uses package
* private apis.
*/
object Build {
  import sbt.internal.TransitiveDynamicInputs._
  val cached = settingKey[Unit]("")
  val newInputs = settingKey[Unit]("")

  val checkCompile = taskKey[Unit]("check compile inputs")
  val checkRun = taskKey[Unit]("check runtime inputs")
  val checkTest = taskKey[Unit]("check test inputs")

  val root = (project in file(".")).settings(
    Compile / cached / fileInputs := (Compile / unmanagedSources / fileInputs).value ++
      (Compile / unmanagedResources / fileInputs).value,
    Test / cached / fileInputs := (Test / unmanagedSources / fileInputs).value ++
      (Test / unmanagedResources / fileInputs).value,
    Compile / newInputs / fileInputs += baseDirectory.value * "*.sc",
    Compile / unmanagedSources / fileInputs ++= (Compile / newInputs / fileInputs).value,
    checkCompile := {
      val actual = (Compile / compile / transitiveDynamicInputs).value.map(_.glob).toSet
      val expected = ((Compile / cached / fileInputs).value ++
        (Compile / newInputs / fileInputs).value).toSet
      streams.value.log.debug(s"actual: $actual\nexpected:$expected")
      if (actual != expected) {
        val actualExtra = actual diff expected
        val expectedExtra = expected diff actual
        throw new IllegalStateException(
          s"$actual did not equal $expected\n" +
            s"${if (actualExtra.nonEmpty) s"Actual result had extra fields $actualExtra" else ""}" +
            s"${if (expectedExtra.nonEmpty) s"Actual result was missing: $expectedExtra" else ""}")
      }
    },
    checkRun := {
      val actual = (Runtime / run / transitiveDynamicInputs).value.map(_.glob).toSet
      // Runtime doesn't add any new inputs, but it should correctly find the Compile inputs via
      // delegation.
      val expected = ((Compile / cached / fileInputs).value ++
        (Compile / newInputs / fileInputs).value).toSet
      streams.value.log.debug(s"actual: $actual\nexpected:$expected")
      if (actual != expected) {
        val actualExtra = actual diff expected
        val expectedExtra = expected diff actual
        throw new IllegalStateException(
          s"${if (actualExtra.nonEmpty) s"Actual result had extra fields: $actualExtra" else ""}" +
            s"${if (expectedExtra.nonEmpty) s"Actual result was missing: $expectedExtra" else ""}")
      }
    },
    checkTest := {
      val actual = (Test / compile / transitiveDynamicInputs).value.map(_.glob).toSet
      val expected = ((Test / cached / fileInputs).value ++
        (Compile / newInputs / fileInputs).value ++ (Compile / cached / fileInputs).value).toSet
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
  )
}