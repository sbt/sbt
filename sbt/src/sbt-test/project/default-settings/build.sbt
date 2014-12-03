
val root = Project("root", file("."), settings=Defaults.defaultSettings)


TaskKey[Unit]("checkArtifacts", "test") := {
	val arts = packagedArtifacts.value
	assert(arts.nonEmpty, "Packaged artifacts must not be empty!")
}