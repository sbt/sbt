ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / ".ivy2")))

// not in the default repositories
libraryDependencies += "com.sun.jmx" % "jmxri" % "1.2.1"

autoScalaLibrary := false