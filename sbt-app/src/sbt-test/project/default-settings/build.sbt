val root = (project in file("."))

TaskKey[Unit]("checkScalaVersion", "test") := {
  val sv = scalaVersion.value
  assert(sv startsWith "2.12.", s"Found $sv!")
}

TaskKey[Unit]("checkArtifacts", "test") := {
  val arts = packagedArtifacts.value
  assert(arts.nonEmpty, "Packaged artifacts must not be empty!")
}
