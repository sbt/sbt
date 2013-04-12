sbtPlugin := true

TaskKey[Unit]("copyOutputDir") <<= (classDirectory in Compile, baseDirectory) map { (cd, base) =>
	IO.copyDirectory(cd, base / "out spaced")
}
