package sbt.watch.task

import sbt._
import Keys._
import sbt.nio.Keys._

object Build {
  val reloadFile = settingKey[File]("file to toggle whether or not to reload")
  val setStringValue = taskKey[Unit]("set a global string to a value")
  val checkStringValue = inputKey[Unit]("check the value of a global")
  val foo = taskKey[Unit]("foo")
  def setStringValueImpl: Def.Initialize[Task[Unit]] = Def.task {
    val i = (setStringValue / fileInputs).value
    val (stringFile, string) = ("foo.txt", "bar")
    val absoluteFile = baseDirectory.value.toPath.resolve(stringFile).toFile
    IO.write(absoluteFile, string)
  }
  def checkStringValueImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val Seq(stringFile, string) = Def.spaceDelimited().parsed
    assert(IO.read(file(stringFile)) == string)
  }
  lazy val root = (project in file(".")).settings(
    reloadFile := baseDirectory.value / "reload",
    foo / fileInputs += baseDirectory.value * "foo.txt",
    setStringValue := Def.taskDyn {
      // This hides foo / fileInputs from the input graph
      Def.taskDyn {
        val _ = (foo / fileInputs).value
          .all(fileTreeView.value, sbt.internal.Continuous.dynamicInputs.value)
        // By putting setStringValueImpl.value inside a Def.task, we ensure that
        // (foo / fileInputs).value is registered with the file repository before modifying the file.
        Def.task(setStringValueImpl.value)
      }
    }.value,
    checkStringValue := checkStringValueImpl.evaluated,
    watchOnInputEvent := { (_, _) =>
      Watch.CancelWatch
    },
    watchOnTriggerEvent := { (_, _) =>
      Watch.CancelWatch
    },
    watchTasks := Def.inputTask {
      val prev = watchTasks.evaluated
      new StateTransform(prev.state.fail)
    }.evaluated
  )
}
