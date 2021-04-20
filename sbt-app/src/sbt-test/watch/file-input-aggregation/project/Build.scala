package sbt
package input.aggregation

import java.nio.file.Paths
import sbt.Keys._
import sbt.internal.DynamicInput
import sbt.nio.Keys._

/**
 * This test is for internal logic so it must be in the sbt package because it uses package
 * private apis.
 */
object Build {
  val setStringValue = inputKey[Unit]("set a global string to a value")
  val checkStringValue = inputKey[Unit]("check the value of a global")
  val checkTriggers = taskKey[Unit]("Check that the triggers are correctly aggregated.")
  val checkGlobs = taskKey[Unit](
    "Check that the globs are correctly aggregated and that the globs are the union of the inputs and the triggers"
  )
  def setStringValueImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val Seq(stringFile, string) = Def.spaceDelimited().parsed.map(_.trim)
    IO.write(file(stringFile), string)
  }
  def checkStringValueImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val Seq(stringFile, string) = Def.spaceDelimited().parsed
    assert(IO.read(file(stringFile)) == string)
  }
  def triggers(t: Seq[DynamicInput]): Seq[Glob] = t.collect {
    // This is a hack to exclude the default compile and resource file inputs
    case i if !i.glob.toString.contains("*") => i.glob
  }

  lazy val foo = project
    .settings(
      setStringValue := {
        val _ = (fileInputs in (bar, setStringValue)).value
        setStringValueImpl.evaluated
      },
      checkStringValue := checkStringValueImpl.evaluated,
      watchOnFileInputEvent := { (_, _) =>
        Watch.CancelWatch
      },
      Compile / compile / watchOnIteration := { (_, _, _) =>
        Watch.CancelWatch
      },
      checkTriggers := {
        val actual = triggers((Compile / compile / transitiveDynamicInputs).value).toSet
        val base = baseDirectory.value.getParentFile.toGlob
        // This checks that since foo depends on bar there is a transitive trigger generated
        // for the "bar.txt" trigger added to bar / Compile / unmanagedResources (which is a
        // transitive dependency of
        val expected: Set[Glob] = Set(base / "baz.txt", base / "bar" / "bar.txt")
        assert(actual == expected)
      },
      Test / test / watchTriggers += baseDirectory.value.toGlob / "test.txt",
      Test / checkTriggers := {
        val testTriggers = triggers((Test / test / transitiveDynamicInputs).value).toSet
        // This validates that since the "test.txt" trigger is only added to the Test / test task,
        // that the Test / compile does not pick it up. Both of them pick up the the triggers that
        // are found in the test above for the compile configuration because of the transitive
        // classpath dependency that is added in Defaults.internalDependencies.
        val compileTriggers = triggers((Test / compile / transitiveDynamicInputs).value).toSet
        val base = baseDirectory.value.getParentFile.toGlob
        val expected: Set[Glob] =
          Set(base / "baz.txt", base / "bar" / "bar.txt", base / "foo" / "test.txt")
        assert(testTriggers == expected)
        assert((testTriggers - (base / "foo" / "test.txt")) == compileTriggers)
      },
    )
    .dependsOn(bar)

  lazy val bar = project.settings(
    fileInputs in setStringValue += baseDirectory.value.toGlob / "foo.txt",
    setStringValue / watchTriggers += baseDirectory.value.toGlob / "bar.txt",
    // This trigger should transitively propagate to foo / compile and foo / Test / compile
    Compile / unmanagedResources / watchTriggers += baseDirectory.value.toGlob / "bar.txt",
    checkTriggers := {
      val base = baseDirectory.value.getParentFile.toGlob
      val actual = triggers((Compile / compile / transitiveDynamicInputs).value).toSet
      val expected: Set[Glob] = Set(base / "bar" / "bar.txt", base / "baz.txt")
      assert(actual == expected)
    },
    // This trigger should not transitively propagate to any foo task
    Test / unmanagedResources / watchTriggers += baseDirectory.value.toGlob / "bar-test.txt",
    Test / checkTriggers := {
      val testTriggers = triggers((Test / test / transitiveDynamicInputs).value).toSet
      val compileTriggers = triggers((Test / compile / transitiveDynamicInputs).value).toSet
      val base = baseDirectory.value.getParentFile.toGlob
      val expected: Set[Glob] =
        Set(base / "baz.txt", base / "bar" / "bar.txt", base / "bar" / "bar-test.txt")
      assert(testTriggers == expected)
      assert(testTriggers == compileTriggers)
    },
  )
  lazy val root = (project in file("."))
    .aggregate(foo, bar)
    .settings(
      watchOnFileInputEvent := { (_, _) =>
        Watch.CancelWatch
      },
      checkTriggers := {
        val actual = triggers((Compile / compile / transitiveDynamicInputs).value)
        val expected: Seq[Glob] = baseDirectory.value.toGlob / "baz.txt" :: Nil
        assert(actual == expected)
      },
    )
}
