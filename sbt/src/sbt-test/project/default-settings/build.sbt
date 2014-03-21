
val root = Project("root", file("."), settings=Defaults.defaultSettings)


TaskKey[Unit]("checkArtifacts", "test") := {
	val arts = packagedArtifacts.value
	assert(!arts.isEmpty, "Packaged artifacts must not be empty!")
}