libraryDependencies += "org.example" %% "artifacta" % "1.0.0-SNAPSHOT" withSources() classifier("test") classifier("")

externalResolvers <<= baseDirectory in ThisBuild map { base => Seq( "demo" at (base  / "demo-repo").toURI.toString ) }

