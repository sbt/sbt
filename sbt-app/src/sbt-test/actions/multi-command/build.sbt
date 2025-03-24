import Build._

organization := "sbt"

name := "scripted-multi-command-parser"

setStringValue := setStringValueImpl.evaluated

checkStringValue := checkStringValueImpl.evaluated

taskThatFails := {
  throw new IllegalArgumentException("")
  ()
}

checkInput := checkInputImpl.evaluated

val dynamicTask = taskKey[Unit]("dynamic input task")

dynamicTask := { println("not yet et") }

crossScalaVersions := "2.11.12" :: "2.12.20" :: Nil
