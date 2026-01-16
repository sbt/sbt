ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

libraryDependencies += "org.testng" % "testng" % "5.7" classifier "jdk15"
