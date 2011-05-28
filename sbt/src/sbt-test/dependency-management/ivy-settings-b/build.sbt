externalIvySettings()

libraryDependencies += "org.scalacheck" % "scalacheck" % "1.5"

TaskKey[Unit]("check") <<= (baseDirectory, update) map { (base, report) =>
	val files = report.matching( moduleFilter(organization = "org.scalacheck", name = "scalacheck", revision = "1.5") )
	assert(!files.isEmpty, "ScalaCheck module not found in update report")
}