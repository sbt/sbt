ivyPaths <<= baseDirectory( dir => new IvyPaths(dir, Some(dir / "ivy-home")))

seq(externalIvySettings(), externalIvyFile())

TaskKey("check") <<= update map { report =>
	val files = report.matching( moduleFilter(organization = "org.scalacheck", name = "scalacheck", revision = "1.5") )
	if(shouldExist)
		assert(!files.isEmpty, "ScalaCheck module not found in update report")
	else
		assert(files.isEmpty, "ScalaCheck module found in update report unexpectedly")
}