ivyPaths <<= baseDirectory( dir => new IvyPaths(dir, Some(dir / "ivy-home")))

resolvers += JavaNet1Repository

libraryDependencies += "javax.ejb" % "ejb-api" % "3.0"
