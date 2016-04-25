val root = (project in file("."))

TaskKey[Unit]("checkArtifacts", "test") := {
  val arts = packagedArtifacts.value
  assert(arts.nonEmpty, "Packaged artifacts must not be empty!")
}
