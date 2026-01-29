val root = (project in file("."))

TaskKey[Unit]("checkScalaVersion", "test") := Def.uncached {
  val sv = scalaVersion.value
  assert(sv startsWith "3.", s"Found $sv!")
}

TaskKey[Unit]("checkArtifacts", "test") := Def.uncached {
  val arts = packagedArtifacts.value
  assert(arts.nonEmpty, "Packaged artifacts must not be empty!")
}
