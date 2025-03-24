ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
ThisBuild / ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))
ThisBuild / managedScalaInstance := false
ThisBuild / autoScalaLibrary  := false
ThisBuild / crossPaths := false
ivyPaths := (ThisBuild / ivyPaths).value
