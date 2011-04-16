ivyScala ~= { (is: Option[IvyScala]) => is.map(_.copy(checkExplicit = false, overrideScalaVersion = false)) }

ivyPaths <<= baseDirectory( dir => new IvyPaths(dir, Some(dir / "ivy-home")))

libraryDependencies += "junit" % "junit" % "4.8"