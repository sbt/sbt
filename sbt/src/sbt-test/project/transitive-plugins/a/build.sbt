publishTo <<= baseDirectory(x =>
	Some(Resolver.file("test-publish", x / "../repo/"))
)

resolvers <+= baseDirectory(x =>
	"test" at (x / "../repo/").asURL.toString
)

name := "demo1"

organization := "org.example"

version := "0.1"

sbtPlugin := true
