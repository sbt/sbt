ivyPaths := (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache")) ).value

publishMavenStyle := false

resolvers := baseDirectory( base => 
	Resolver.file("test-repo", base / "repo" / "test")(Resolver.defaultIvyPatterns) :: Nil
).value

libraryDependencies := (baseDirectory { base =>
	val color = IO.read(base / "color")
	val dep = "org.scala-sbt" %% "define-color" % "1.0" extra("e:color" -> color)
	dep :: Nil
}).value

organization := "org.example"

name := "use-color"