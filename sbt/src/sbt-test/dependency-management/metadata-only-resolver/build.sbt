ivyPaths := (baseDirectory, target)( (dir, t) => IvyPaths(dir, Some(t / ".ivy2"))).value

// not in the default repositories
libraryDependencies += "com.sun.jmx" % "jmxri" % "1.2.1"

autoScalaLibrary := false
