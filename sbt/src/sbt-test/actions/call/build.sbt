sbtPlugin := true

val copyOutputDir = taskKey[Unit]("Copies the compiled classes to a root-level directory")

copyOutputDir := {
	val cd = (classDirectory in Compile).value
	val to = baseDirectory.value / "out spaced"
	IO.copyDirectory(cd, to)
}
