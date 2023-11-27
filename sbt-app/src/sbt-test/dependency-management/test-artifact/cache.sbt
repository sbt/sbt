ThisBuild / useCoursier := false
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

ivyPaths := {
	val base = baseDirectory.value
	IvyPaths(base.toString, Some((base / "ivy-cache").toString))
}

managedScalaInstance := false
autoScalaLibrary := false
crossPaths := false
