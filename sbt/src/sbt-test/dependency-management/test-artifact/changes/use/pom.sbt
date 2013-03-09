libraryDependencies += "org.example" % "def" % "2.0" classifier("tests")

externalResolvers := Seq("example" at (baseDirectory.value / "ivy-repo").toURI.toString)
