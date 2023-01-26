lazy val check = taskKey[Unit]("")
lazy val root = (project in file("."))
lazy val foo = project
lazy val bar = project

def scala212 = "2.12.17"
scalaVersion := scala212

check := {
  assert((root / scalaVersion).value == scala212)
  assert((foo / scalaVersion).value == scala212)
  assert((bar / scalaVersion).value == scala212)
}
