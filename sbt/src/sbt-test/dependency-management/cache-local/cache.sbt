ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
ivyPaths := { IvyPaths(baseDirectory.value, Some(target.value / ".ivy2")) }
