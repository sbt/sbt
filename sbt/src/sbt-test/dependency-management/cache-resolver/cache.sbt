ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
ThisBuild / ivyPaths := {
	val base = (ThisBuild / baseDirectory).value
	IvyPaths(base, Some(base / "ivy-cache"))
}
ThisBuild / managedScalaInstance := false
ThisBuild / autoScalaLibrary  := false
ThisBuild / crossPaths := false
