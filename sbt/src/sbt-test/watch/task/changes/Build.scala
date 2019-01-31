package sbt.watch.task

import sbt._
import Keys._

object Build {
  val setStringValue = inputKey[Unit]("set a global string to a value")
  val checkStringValue = inputKey[Unit]("check the value of a global")
  def setStringValueImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val Seq(stringFile, string) = Def.spaceDelimited().parsed.map(_.trim)
    IO.write(file(stringFile), string)
  }
  def checkStringValueImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val Seq(stringFile, string) = Def.spaceDelimited().parsed
    assert(IO.read(file(stringFile)) == string)
  }
  lazy val root = (project in file(".")).settings(
    setStringValue / watchTriggers += baseDirectory.value * "foo.txt",
    setStringValue := setStringValueImpl.evaluated,
    checkStringValue := checkStringValueImpl.evaluated,
    watchStartMessage := { _ =>
      IO.touch(baseDirectory.value / "foo.txt", true)
      Some("watching")
    },
    watchOnStart := { _ => () => Watched.CancelWatch }
  )
}