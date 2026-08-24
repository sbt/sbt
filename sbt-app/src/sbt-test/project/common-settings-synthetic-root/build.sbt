lazy val check = taskKey[Unit]("")

def scala212 = "2.12.21"
scalaVersion := scala212
val o = "com.example"
organization := o

lazy val aa = settingKey[Seq[String]]("")
Global / aa := Seq("initial-value")
// explicitly ThisBuild-scoped settings must apply exactly once, not once per project (#9668)
ThisBuild / aa += "added-value"

lazy val bb = settingKey[Seq[String]]("")
Global / bb := Seq("initial-value")
// ThisProject means "current project", so it must still apply once per project, unlike ThisBuild
ThisProject / bb += "added-value"

lazy val foo = project
lazy val bar = project
  .settings(
    name := "bar",
    organization := "com.example.bar",
  )

lazy val baz = project

check := {
  assert((foo / scalaVersion).value == scala212)
  assert((bar / scalaVersion).value == scala212)
  assert((baz / scalaVersion).value == scala212)

  assert((foo / organization).value == o)
  // Test that bar can override common setting in settings(...)
  assert((bar / organization).value == "com.example.bar", s"unexpected bar / organization = {(bar / organization).value}")
  // Test that baz/build.sbt bare settings get loaded
  assert((baz / organization).value == "com.example.baz", s"unexpected baz/organization")

  // Test that ThisBuild / aa += is applied exactly once, not once per project (#9668)
  val expectedAa = Seq("initial-value", "added-value")
  assert((foo / aa).value == expectedAa, s"(foo / aa).value: ${(foo / aa).value}")
  assert((bar / aa).value == expectedAa, s"(bar / aa).value: ${(bar / aa).value}")
  assert((baz / aa).value == expectedAa, s"(baz / aa).value: ${(baz / aa).value}")

  // Test that ThisProject / bb += still applies once per project (#9668)
  val expectedBb = Seq("initial-value", "added-value")
  assert((foo / bb).value == expectedBb, s"(foo / bb).value: ${(foo / bb).value}")
  assert((bar / bb).value == expectedBb, s"(bar / bb).value: ${(bar / bb).value}")
  assert((baz / bb).value == expectedBb, s"(baz / bb).value: ${(baz / bb).value}")
}
check / aggregate := false
