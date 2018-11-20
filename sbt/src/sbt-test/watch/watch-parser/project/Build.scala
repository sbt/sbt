import sbt._

object Build {
  private[this] var string: String = ""
  private[this] val stringFile = file("string.txt")
  val setStringValue = inputKey[Unit]("set a global string to a value")
  val checkStringValue = inputKey[Unit]("check the value of a global")
  def setStringValueImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    string = Def.spaceDelimited().parsed.mkString(" ").trim
    IO.write(stringFile, string)
  }
  def checkStringValueImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    assert(string == Def.spaceDelimited().parsed.mkString(" ").trim)
    assert(IO.read(stringFile) == string)
  }
}