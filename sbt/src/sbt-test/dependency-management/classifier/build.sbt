ivyPaths := IvyPaths(baseDirectory.value, Some(target.value / "ivy-cache"))

libraryDependencies += "org.testng" % "testng" % "5.7" classifier "jdk15"
