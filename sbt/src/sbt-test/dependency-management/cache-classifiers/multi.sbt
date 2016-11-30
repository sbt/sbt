def localCache = 
	ivyPaths := IvyPaths(baseDirectory.value, Some((baseDirectory in ThisBuild).value / "ivy" / "cache"))

val b = project.settings(localCache)

val a = project.settings(localCache)
