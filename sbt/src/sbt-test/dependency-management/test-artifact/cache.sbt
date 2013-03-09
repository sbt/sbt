ivyPaths := {
	val base = baseDirectory.value
	new IvyPaths(base, Some(base / "ivy-cache"))
}

managedScalaInstance := false

autoScalaLibrary := false

crossPaths := false
