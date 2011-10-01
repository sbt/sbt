publishTo <<= baseDirectory(x =>
	Some(Resolver.file("test-publish", x / "../repo/"))
)

resolvers <+= baseDirectory(x =>
	"test" at (x / "../repo/").asURL.toString
)

name := "demo3"

organization := "org.example"

version := "0.3"

sbtPlugin := true

//addSbtPlugin("org.example" % "demo1" % "0.1")

addSbtPlugin("org.example" % "demo2" % "0.2")
