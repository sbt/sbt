val projA = project in file("projA")

val projB = project in file("projB")

lazy val check = taskKey[Unit]("Verifies expected build behavior")

check := {
  val projBDeps = (dependencyClasspath in (projB, Compile)).value.map(_.data)
  val assertion = projBDeps.filter(_.getAbsolutePath.contains("projA")).nonEmpty
  assert(assertion, "Unable to find projA classes in projB's dependency list")
}
