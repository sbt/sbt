name := "transitive-lib"

organization := "sbt"

resolvers += "Local Maven" at file("../ivy").getCanonicalFile.toURI.toURL.toString

publishTo := Some(Resolver.file("test-resolver", file("..").getCanonicalFile / "ivy"))

version := "0.1.0"

libraryDependencies += "sbt" % "foo-lib" % "0.1.0"

crossPaths := false

autoScalaLibrary := false
