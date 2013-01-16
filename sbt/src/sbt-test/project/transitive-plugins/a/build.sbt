publishTo <<= (baseDirectory in ThisBuild)(x =>
	Some(Resolver.file("test-publish", x / "repo/"))
)

resolvers <+= (baseDirectory in ThisBuild)(x =>
	"test" at (x / "repo/").asURL.toString
)

name := "demo1"

organization := "org.example"

version := "0.1"

sbtPlugin := true
