import sbt._

import sbt.internal.util.complete.Parser._

object Build {
  private[this] var string: String = ""
  private[this] val stringFile = file("string.txt")
  val setStringValue = inputKey[Unit]("set a global string to a value")
  val checkStringValue = inputKey[Unit]("check the value of a global")
  val taskThatFails = taskKey[Unit]("this should fail")
  val checkInputContainsSemicolon = inputKey[Unit]("this should extract arguments that are semicolon delimited")
  def setStringValueImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    string = Def.spaceDelimited().parsed.mkString(" ").trim
    IO.write(stringFile, string)
  }
  def checkStringValueImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val actual = Def.spaceDelimited().parsed.mkString(" ").trim
    assert(string == actual)
    assert(IO.read(stringFile) == string)
  }

  def checkInputContainsSemicolonImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val actual = (charClass(_ != ';').+ <~ ';'.?).map(_.mkString.trim).+.parsed
    assert(actual == Seq("foo", "bar"))
  }
}
