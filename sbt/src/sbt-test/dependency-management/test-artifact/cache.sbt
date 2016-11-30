ivyPaths := {
	val base = baseDirectory.value
	IvyPaths(base, Some(base / "ivy-cache"))
}

managedScalaInstance := false

autoScalaLibrary := false

crossPaths := false
