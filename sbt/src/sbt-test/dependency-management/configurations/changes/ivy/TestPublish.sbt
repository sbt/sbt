publishMavenStyle := false

publishTo <<= baseDirectory { base =>
	Some( Resolver.file("test-repo", base / "repo")(Patterns(false, Resolver.mavenStyleBasePattern)) )
}

name := "test-ivy"

organization := "org.example"

version := "1.0"
