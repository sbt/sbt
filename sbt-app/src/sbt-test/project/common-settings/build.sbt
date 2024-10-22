lazy val check = taskKey[Unit]("")

def scala212 = "2.12.20"
scalaVersion := scala212
val o = "com.example"
organization := o

lazy val root = (project in file("."))
  .aggregate(foo, bar, baz)

lazy val foo = project
lazy val bar = project
  .settings(
    name := "bar",
    organization := "com.example.bar",
  )

lazy val baz = project

check := {
  assert((root / scalaVersion).value == scala212)
  assert((foo / scalaVersion).value == scala212)
  assert((bar / scalaVersion).value == scala212)
  assert((baz / scalaVersion).value == scala212)

  assert((root / organization).value == o)
  assert((foo / organization).value == o)
  // Test that bar can override common setting in settings(...)
  assert((bar / organization).value == "com.example.bar")
  // Test that baz/build.sbt bare settings get loaded
  assert((baz / organization).value == "com.example.baz")
}
check / aggregate := false
