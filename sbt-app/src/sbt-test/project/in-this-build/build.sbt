val a = settingKey[Int]("an int")

inThisBuild (
  a := 1
)

val p = project

TaskKey[Unit]("check") := {
  assert((p / a).?.value == Option(1), s"a in p should be Some(1) but is ${(p / a).?.value}")
}
