scalaVersion in ThisBuild := "2.7.7"

scalaVersion := "2.9.1"

scalaVersion in update := {
  scalaVersion.value match {
    case "2.9.1" => "2.9.0-1"
    case "2.8.2" => "2.8.1"
    case x => x
  }
}

InputKey[Unit]("check") := {
  val args = Def.spaceDelimited().parsed
  def check(label: String, i: Int, actual: String) =
    assert(args(i) == actual, s"Expected $label='${args(i)}' got '$actual'")
  check("scalaVersion in ThisBuild", 0, scalaVersion in ThisBuild value)
  check("scalaVersion", 1, scalaVersion.value)
  check("scalaVersion in update", 2, scalaVersion in update value)
}
