libraryDependencies += "org.example" %% "artifacta" % "1.0.0-SNAPSHOT" withSources() classifier("tests") classifier("")

externalResolvers := Seq( "demo" at ( (baseDirectory in ThisBuild).value / "demo-repo").toURI.toString )

