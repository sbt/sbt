lazy val check = taskKey[Unit]("Verifies overrideBuildResolvers is true when repositories_force exists")

lazy val root = (project in file(".")).settings(
  check := {
    val overrideOn = overrideBuildResolvers.value
    assert(overrideOn, "overrideBuildResolvers should be true when global/repositories_force exists")
  }
)
