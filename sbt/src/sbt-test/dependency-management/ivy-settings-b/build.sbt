externalIvySettings()

libraryDependencies += "org.scalacheck" % "scalacheck" % "1.5"

TaskKey[Unit]("check") := {
  val base = baseDirectory.value
  val report = update.value
	val files = report.matching( moduleFilter(organization = "org.scalacheck", name = "scalacheck", revision = "1.5") )
	assert(files.nonEmpty, "ScalaCheck module not found in update report")
}
