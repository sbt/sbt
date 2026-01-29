lazy val a = project.dependsOn(b)
  .settings(scalaVersion := "2.9.3")
lazy val b = RootProject(uri("b"))
lazy val check = taskKey[Unit]("Checks the configured scalaBinaryVersion")

check := {
	val av = (a / scalaBinaryVersion).value
	val bv = (b / scalaBinaryVersion).value
	same(av, "2.9.3")
	same(bv, "2.10")
}

def same(actual: String, expected: String): Unit = {
  assert(actual == expected, s"Expected binary version to be $expected, was $actual")
}
