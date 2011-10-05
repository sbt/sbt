seq(externalIvySettings(), externalIvyFile())

TaskKey[Unit]("check") <<= (baseDirectory, update) map { (base, report) =>
	val files = report.matching( moduleFilter(organization = "org.scala-tools.testing", name = "scalacheck*", revision = "1.9") )
	assert(!files.isEmpty, "ScalaCheck module not found in update report")
}