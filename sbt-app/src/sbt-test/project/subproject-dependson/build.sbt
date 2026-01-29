val projA = (project in file("projA"))

val projB = (project in file("projB"))

lazy val check = taskKey[Unit]("Verifies expected build behavior")

LocalRootProject / check := {
  val conv = fileConverter.value
  val projBDeps = (projB / Compile / dependencyClasspath).value
    .map(_.data)
  val assertion = (projBDeps.filter: x =>
    conv.toPath(x).toString().contains("proja")
  ).nonEmpty
  assert(assertion,
    s"Unable to find projA classes in projB's dependency list ${projBDeps}"
  )
}
