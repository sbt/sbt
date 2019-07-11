ThisBuild / turbo := true

resolvers += "Local Maven" at (baseDirectory.value / "libraries" / "foo" / "ivy").toURI.toURL.toString

libraryDependencies += "sbt" %% "foo-lib" % "0.1.0"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test"
