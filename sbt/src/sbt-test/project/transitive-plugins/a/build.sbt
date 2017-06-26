publishTo := Some(Resolver.file("test-publish", (baseDirectory in ThisBuild).value / "repo/"))
resolvers += ("test" at ((baseDirectory in ThisBuild).value / "repo/").asURL.toString)
resolvers += Resolver.mavenLocal

name := "demo1"

organization := "org.example"

version := "0.1"

sbtPlugin := true
