// the default, but make it explicit
publishMavenStyle := true

publishTo <<= baseDirectory(bd => Some( MavenRepository("test-repo", (bd / "repo").toURI.toASCIIString )) )

name := "test"

organization := "org.example"

version := "1.0"
