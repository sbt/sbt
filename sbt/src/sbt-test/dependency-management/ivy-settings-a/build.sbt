import sbt.internal.librarymanagement.syntax._

seq(externalIvySettings(), externalIvyFile())

TaskKey[Unit]("check") := {
	val files = update.value.matching( moduleFilter(organization = "org.scalacheck", name = "scalacheck*", revision = "1.11.4") )
	assert(files.nonEmpty, "ScalaCheck module not found in update report")
}