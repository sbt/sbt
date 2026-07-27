@transient
lazy val check = taskKey[Unit]("")

def scala212 = "2.12.21"
scalaVersion := scala212
val o = "com.example"
organization := o

lazy val root = rootProject
  .autoAggregate

lazy val foo = project
lazy val bar = project
  .settings(
    name := "bar",
    organization := "com.example.bar",
  )

lazy val baz = project
lazy val qux = project

LocalRootProject / check := {
  assert((root / scalaVersion).value == scala212)
  assert((foo / scalaVersion).value == scala212)
  assert((bar / scalaVersion).value == scala212)
  assert((baz / scalaVersion).value == scala212)
  assert((qux / scalaVersion).value == scala212)

  assert((root / organization).value == o, s"(root / organization).value: ${(root / organization).value}")
  assert((foo / organization).value == o, s"(foo / organization).value: ${(foo / organization).value}")
  // Test that bar can override common setting in settings(...)
  assert((bar / organization).value == "com.example.bar")
  // Test that baz/build.sbt bare settings get loaded
  assert((baz / organization).value == "com.example.baz")
  // Test that baz/build.sbt settings don't leak onto qux, processed right after it (#9517)
  assert((qux / organization).value == o, s"(qux / organization).value: ${(qux / organization).value}")
}
check / aggregate := false
