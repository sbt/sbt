ivyPaths := IvyPaths(baseDirectory.value, Some(target.value / "ivy-cache"))
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

libraryDependencies += "org.testng" % "testng" % "5.7" classifier "jdk15"
