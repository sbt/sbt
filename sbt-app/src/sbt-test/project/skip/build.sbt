publish / skip := true

lazy val check = taskKey[Unit]("check")

lazy val a = project
  .in(file("a"))
  .settings(
    publishLocal / skip := true
  )

lazy val b = project
  .in(file("b"))

check := {
  assert((publishLocal / skip).value, "Expected true, got false")
  assert((a / publishLocal / skip).value, "Expected true, got false")
  assert(!(a / publish / skip).value, "Expected false, got true")
  assert(!(b / publish / skip).value, "Expected false, got true")
  assert(!(b / publishLocal / skip).value, "Expected false, got true")
}
