// https://github.com/sbt/sbt/issues/9269
val `kebab-case-project` = rootProject

InputKey[Unit]("checkVersion") := {
  val actual = version.value
  val expect = Def.spaceDelimited("").parsed.head
  assert(expect == actual, (actual, expect))
}
