package sbt.input.aggregation

import sbt.Keys._
import sbt._
import sbt.internal.TransitiveGlobs._
import sbt.nio.Keys._
import sbt.nio.file._

object Build {
  val setStringValue = inputKey[Unit]("set a global string to a value")
  val checkStringValue = inputKey[Unit]("check the value of a global")
  val checkTriggers = taskKey[Unit]("Check that the triggers are correctly aggregated.")
  val checkGlobs = taskKey[Unit]("Check that the globs are correctly aggregated and that the globs are the union of the inputs and the triggers")
  def setStringValueImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val Seq(stringFile, string) = Def.spaceDelimited().parsed.map(_.trim)
    IO.write(file(stringFile), string)
  }
  def checkStringValueImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val Seq(stringFile, string) = Def.spaceDelimited().parsed
    assert(IO.read(file(stringFile)) == string)
  }
  def checkGlobsImpl: Def.Initialize[Task[Unit]] = Def.task {
    val (globInputs, globTriggers) = (Compile / compile / transitiveGlobs).value
    val inputs = (Compile / compile / transitiveInputs).value.toSet
    val triggers = (Compile / compile / transitiveTriggers).value.toSet
    assert(globInputs.toSet == inputs)
    assert(globTriggers.toSet == triggers)
  }
  lazy val foo = project.settings(
    setStringValue := {
      val _ = (fileInputs in (bar, setStringValue)).value
      setStringValueImpl.evaluated
    },
    checkStringValue := checkStringValueImpl.evaluated,
    watchOnTriggerEvent := { (_, _) => Watch.CancelWatch },
    watchOnInputEvent := { (_, _) => Watch.CancelWatch },
    Compile / compile / watchOnStart := { _ => () => Watch.CancelWatch },
    checkTriggers := {
      val actual = (Compile / compile / transitiveTriggers).value.toSet
      val base = baseDirectory.value.getParentFile
      // This checks that since foo depends on bar there is a transitive trigger generated
      // for the "bar.txt" trigger added to bar / Compile / unmanagedResources (which is a
      // transitive dependency of
      val expected: Set[Glob] = Set(base * "baz.txt", (base / "bar") * "bar.txt")
      assert(actual == expected)
    },
    Test / test / watchTriggers += baseDirectory.value * "test.txt",
    Test / checkTriggers := {
      val testTriggers = (Test / test / transitiveTriggers).value.toSet
      // This validates that since the "test.txt" trigger is only added to the Test / test task,
      // that the Test / compile does not pick it up. Both of them pick up the the triggers that
      // are found in the test above for the compile configuration because of the transitive
      // classpath dependency that is added in Defaults.internalDependencies.
      val compileTriggers = (Test / compile / transitiveTriggers).value.toSet
      val base = baseDirectory.value.getParentFile
      val expected: Set[Glob] = Set(
        base * "baz.txt", (base / "bar") * "bar.txt", (base / "foo") * "test.txt")
      assert(testTriggers == expected)
      assert((testTriggers - ((base / "foo") * "test.txt")) == compileTriggers)
    },
    checkGlobs := checkGlobsImpl.value
  ).dependsOn(bar)
  lazy val bar = project.settings(
    fileInputs in setStringValue += baseDirectory.value * "foo.txt",
    setStringValue / watchTriggers += baseDirectory.value * "bar.txt",
    // This trigger should transitively propagate to foo / compile and foo / Test / compile
    Compile / unmanagedResources / watchTriggers += baseDirectory.value * "bar.txt",
    checkTriggers := {
      val base = baseDirectory.value.getParentFile
      val actual = (Compile / compile / transitiveTriggers).value
      val expected: Set[Glob] = Set((base / "bar") * "bar.txt", base * "baz.txt")
      assert(actual.toSet == expected)
    },
    // This trigger should not transitively propagate to any foo task
    Test / unmanagedResources / watchTriggers += baseDirectory.value * "bar-test.txt",
    Test / checkTriggers := {
      val testTriggers = (Test / test / transitiveTriggers).value.toSet
      val compileTriggers = (Test / compile / transitiveTriggers).value.toSet
      val base = baseDirectory.value.getParentFile
      val expected: Set[Glob] = Set(
        base * "baz.txt", (base / "bar") * "bar.txt", (base / "bar") * "bar-test.txt")
      assert(testTriggers == expected)
      assert(testTriggers == compileTriggers)
    },
    checkGlobs := checkGlobsImpl.value
  )
  lazy val root = (project in file(".")).aggregate(foo, bar).settings(
    watchOnEvent := { _ => _ => Watch.CancelWatch },
    checkTriggers := {
      val actual = (Compile / compile / transitiveTriggers).value
      val expected: Seq[Glob] = baseDirectory.value * "baz.txt" :: Nil
      assert(actual == expected)
    },
    checkGlobs := checkGlobsImpl.value
  )
}
