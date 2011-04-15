ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache")))

publishMavenStyle := false

publishTo <<= baseDirectory { base =>
	Some(Resolver.file("test-repo", base / "repo" / "test")(Resolver.defaultIvyPatterns))
}

projectID <<= projectID { _.extra("e:color" -> "red") }

organization := "org.scala-tools.sbt"

version := "1.0"

name := "define-color"