lazy val a = project.dependsOn(b).settings(scalaVersion := "2.8.2")
lazy val b = RootProject(uri("b"))
lazy val check = taskKey[Unit]("Checks the configured scalaBinaryVersion")

check := {
	val av = (scalaBinaryVersion in a).value
	val bv = (scalaBinaryVersion in b).value
	same(av, "2.8.2")
	same(bv, "2.10")
}

def same(actual: String, expected: String): Unit = {
  assert(actual == expected, s"Expected binary version to be $expected, was $actual")
}
