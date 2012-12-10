seq(externalIvySettings(), externalIvyFile())

TaskKey[Unit]("check") := {
	val files = update.value.matching( moduleFilter(organization = "org.scalacheck", name = "scalacheck*", revision = "1.9") )
	assert(!files.isEmpty, "ScalaCheck module not found in update report")
}