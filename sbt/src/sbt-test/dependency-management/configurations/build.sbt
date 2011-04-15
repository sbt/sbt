// the default, but make it explicit
publishMavenStyle := true

publishTo <<= baseDirectory(bd => Some( Resolver.file("test-repo", bd / "repo") ) )

name := "test"

organization := "org.example"

version := "1.0"
