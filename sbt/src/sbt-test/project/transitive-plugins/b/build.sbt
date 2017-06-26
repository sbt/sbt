publishTo := Some(Resolver.file("test-publish", (baseDirectory in ThisBuild).value / "repo"))
resolvers += ("test" at ((baseDirectory in ThisBuild).value / "repo").asURL.toString)
resolvers += Resolver.mavenLocal

name := "demo2"

organization := "org.example"

version := "0.2"

sbtPlugin := true

addSbtPlugin("org.example" % "demo1" % "0.1")
