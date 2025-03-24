ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

// not in the default repositories
libraryDependencies += "com.sun.jmx" % "jmxri" % "1.2.1"

autoScalaLibrary := false
