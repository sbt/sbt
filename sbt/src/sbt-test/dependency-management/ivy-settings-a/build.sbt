externalIvySettings()
externalIvyFile()

TaskKey[Unit]("check") := {
  val ur = update.value
  val files = ur.matching( moduleFilter(organization = "org.scalacheck", name = "scalacheck*", revision = "1.13.4") )
  assert(files.nonEmpty, "ScalaCheck module not found in update report")
}
