scalaVersion := "2.10.7"
resolvers += Resolver.sonatypeRepo("staging")
conflictManager := ConflictManager.strict.copy(organization = "^(?!org\\.scala-lang).*$")

libraryDependencies += "org.specs2" %% "specs2" % "2.3.10-scalaz-7.1.0-M6" % "test"
