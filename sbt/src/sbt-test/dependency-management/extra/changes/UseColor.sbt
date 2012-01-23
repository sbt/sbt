ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache")) )

publishMavenStyle := false

resolvers <<= baseDirectory( base => 
	Resolver.file("test-repo", base / "repo" / "test")(Resolver.defaultIvyPatterns) :: Nil
)

libraryDependencies <<= baseDirectory { base =>
	val color = IO.read(base / "color")
	val dep = "org.scala-sbt" %% "define-color" % "1.0" extra("e:color" -> color)
	dep :: Nil
}

organization := "org.example"

name := "use-color"