scalaVersion := "2.10.4"

conflictManager := ConflictManager.strict.withOrganization("^(?!org\\.scala-lang).*$")

libraryDependencies += "org.specs2" %% "specs2" % "2.3.10-scalaz-7.1.0-M6" % "test"
