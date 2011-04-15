ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache")))

libraryDependencies += "org.testng" % "testng" % "5.7" classifier "jdk15"
