ThisBuild / scalaVersion := "2.13.12"
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

// don't blow up when credential file doesn't exist
// https://github.com/sbt/sbt/issues/4882
credentials += Credentials(baseDirectory.value / "nonexistent")
