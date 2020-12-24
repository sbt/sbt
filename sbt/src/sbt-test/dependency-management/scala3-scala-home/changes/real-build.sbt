scalaVersion := "3.0.0-M2"
scalaHome := Some(baseDirectory.value / "home")

val checkUpdate = taskKey[Unit]("Ensures that resolved Scala artifacts are replaced with ones from the configured Scala home directory")

checkUpdate := {
	val report = update.value
	val lib = (scalaHome.value.get / "lib").getCanonicalFile
	for(f <- report.allFiles)
		assert(f.getParentFile == lib, "Artifact not in Scala home directory: " + f.getAbsolutePath)
}
