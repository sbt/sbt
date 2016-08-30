publishTo := (baseDirectory in ThisBuild)(x =>
	Some(Resolver.file("test-publish", x / "repo"))
).value

resolvers += (baseDirectory in ThisBuild)(x =>
	"test" at (x / "repo").asURL.toString
).value

name := "demo3"

organization := "org.example"

version := "0.3"

sbtPlugin := true

//addSbtPlugin("org.example" % "demo1" % "0.1")

addSbtPlugin("org.example" % "demo2" % "0.2")
