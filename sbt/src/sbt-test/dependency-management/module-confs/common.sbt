ivyScala ~= { (is: Option[IvyScala]) => is.map(_.copy(checkExplicit = false, overrideScalaVersion = false, filterImplicit = false)) }

ivyPaths := baseDirectory( dir => IvyPaths(dir, Some(dir / "ivy-home"))).value

libraryDependencies += "junit" % "junit" % "4.8"

autoScalaLibrary := false

cleanFiles += baseDirectory(_ / "ivy-home").value
