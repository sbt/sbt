publishTo := (baseDirectory in ThisBuild)(x =>
	Some(Resolver.file("test-publish", x / "repo/"))
).value

resolvers += (baseDirectory in ThisBuild)(x =>
	"test" at (x / "repo/").asURL.toString
).value

name := "demo1"

organization := "org.example"

version := "0.1"

sbtPlugin := true
