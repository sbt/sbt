import sbt._

import Keys.baseDirectory

object Build {
  val setStringValue = inputKey[Unit]("set a global string to a value")
  val checkStringValue = inputKey[Unit]("check the value of a global")
  def setStringValueImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val Seq(stringFile, string) = Def.spaceDelimited().parsed
    IO.write(baseDirectory.value / stringFile, string)
  }
  def checkStringValueImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val Seq(stringFile, string) = Def.spaceDelimited().parsed
    assert(IO.read(baseDirectory.value / stringFile) == string)
  }
}