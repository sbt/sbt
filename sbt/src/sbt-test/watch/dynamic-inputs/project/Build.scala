package sbt.watch.task

import java.nio.file.Path
import sbt._
import Keys._
import sbt.nio.Keys._
import sbt.nio.Watch

object Build {
  val reloadFile = settingKey[File]("file to toggle whether or not to reload")
  val setStringValue = taskKey[Unit]("set a global string to a value")
  val checkStringValue = inputKey[Unit]("check the value of a global")
  val foo = taskKey[Seq[Path]]("foo")
  def setStringValueImpl: Def.Initialize[Task[Unit]] = Def.task {
    val i = (setStringValue / fileInputs).value
    val (stringFile, string) = ("foo.txt", "bar")
    val absoluteFile = baseDirectory.value.toPath.resolve(stringFile).toFile
    IO.write(absoluteFile, string)
    println(s"wrote to $absoluteFile")
  }
  def checkStringValueImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val Seq(stringFile, string) = Def.spaceDelimited().parsed
    assert(IO.read(file(stringFile)) == string)
  }
  lazy val root = (project in file(".")).settings(
    reloadFile := baseDirectory.value / "reload",
    foo / fileInputs += baseDirectory.value.toGlob / "foo.txt",
    foo := foo.inputFiles,
    setStringValue := Def.taskDyn {
      // This hides foo / fileInputs from the input graph
      Def.taskDyn {
        val inputs = foo.value
        // By putting setStringValueImpl.value inside a Def.task, we ensure that
        // (foo / fileInputs).value is registered with the file repository before modifying the file.
        if (inputs.isEmpty) Def.task(setStringValueImpl.value)
        else Def.task(assert(false))
      }
    }.value,
    checkStringValue := checkStringValueImpl.evaluated,
    watchOnFileInputEvent := { (_, _) => Watch.CancelWatch },
  )
}
