ivyPaths := (baseDirectory, target)( (dir, t) => IvyPaths(dir, Some(t / "ivy-cache"))).value

publishMavenStyle := false

publishTo := (baseDirectory { base =>
	Some(Resolver.file("test-repo", base / "repo" / "test")(Resolver.defaultIvyPatterns))
}).value

projectID := (projectID { _.extra("e:color" -> "red") }).value

organization := "org.scala-sbt"

version := "1.0"

name := "define-color"
