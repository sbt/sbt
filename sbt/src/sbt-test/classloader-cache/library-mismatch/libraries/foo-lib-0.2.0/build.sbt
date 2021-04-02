name := "foo-lib"

organization := "sbt"

publishTo := Some(Resolver.file("test-resolver", file("..").getCanonicalFile / "ivy"))

version := "0.2.0"

crossPaths := false

autoScalaLibrary := false
