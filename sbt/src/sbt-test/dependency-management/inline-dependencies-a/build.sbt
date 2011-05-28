libraryDependencies += "org.scalacheck" % "scalacheck" % "1.5"

ivyPaths <<= baseDirectory( dir => new IvyPaths(dir, Some(dir / "ivy-home")))

TaskKey[Unit]("check") <<= update map { report =>
	val files = report.matching( moduleFilter(organization = "org.scalacheck", name = "scalacheck", revision = "1.5") )
	assert(!files.isEmpty, "ScalaCheck module not found in update report")
	val missing = files.filter(! _.exists)
	assert(missing.isEmpty, "Reported ScalaCheck artifact files don't exist: " + missing.mkString(", "))
}