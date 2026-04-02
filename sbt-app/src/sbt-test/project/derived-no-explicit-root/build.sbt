@transient
lazy val check = taskKey[Unit]("check")

check := {
  val v = (LocalProject("foo") / DerivedProjectPlugin.value1).value
  assert(v == 3, s"Expected 3 but got $v")
}
