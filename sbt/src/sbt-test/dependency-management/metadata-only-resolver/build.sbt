ivyPaths := IvyPaths(baseDirectory.value, Some(target.value / ".ivy2"))

// not in the default repositories
libraryDependencies += "com.sun.jmx" % "jmxri" % "1.2.1"

autoScalaLibrary := false
