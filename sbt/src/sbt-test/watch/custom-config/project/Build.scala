package sbt.input.aggregation

import sbt._
import Keys._
import sbt.nio.Keys._

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
  lazy val foo = project.settings(
    watchStartMessage := { (count: Int, _, _) => Some(s"FOO $count") },
    Compile / compile / watchTriggers += baseDirectory.value * "foo.txt",
    Compile / compile / watchStartMessage := { (count: Int, _, _) =>
      // this checks that Compile / compile / watchStartMessage
      // is preferred to Compile / watchStartMessage
      val outputFile = baseDirectory.value / "foo.txt"
      IO.write(outputFile, "compile")
      Some(s"compile $count")
    },
    Compile / watchStartMessage := { (count: Int, _, _) => Some(s"Compile $count") },
    Runtime / watchStartMessage := { (count: Int, _, _) => Some(s"Runtime $count") },
    setStringValue := {
      val _ = (fileInputs in (bar, setStringValue)).value
      setStringValueImpl.evaluated
    },
    checkStringValue := checkStringValueImpl.evaluated,
    watchOnEvent := { _ => _ => Watch.CancelWatch }
  )
  lazy val bar = project.settings(fileInputs in setStringValue += baseDirectory.value * "foo.txt")
  lazy val root = (project in file(".")).aggregate(foo, bar).settings(
    watchOnEvent := { _ => _ => Watch.CancelWatch }
  )
}
