val a = settingKey[Int]("an int")

inThisBuild (
  a := 1
)

val p = project

TaskKey[Unit]("check") := {
  assert((a in p).?.value == Option(1), s"a in p should be Some(1) but is ${(a in p).?.value}")
}
