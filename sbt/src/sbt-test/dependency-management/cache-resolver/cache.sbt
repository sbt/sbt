ivyPaths in ThisBuild := {
	val base = (baseDirectory in ThisBuild).value
	IvyPaths(base, Some(base / "ivy-cache"))
}
managedScalaInstance in ThisBuild := false
autoScalaLibrary in ThisBuild := false
crossPaths in ThisBuild := false
