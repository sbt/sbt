ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

ivyScala ~= { (is: Option[IvyScala]) => is.map(_.copy(checkExplicit = false, overrideScalaVersion = false, filterImplicit = false)) }

ivyPaths := baseDirectory( dir => IvyPaths(dir, Some(dir / "ivy-home"))).value

libraryDependencies += "junit" % "junit" % "4.13.1"

autoScalaLibrary := false

cleanFiles += baseDirectory(_ / "ivy-home").value
