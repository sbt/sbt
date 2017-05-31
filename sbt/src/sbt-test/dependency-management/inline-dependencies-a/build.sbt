libraryDependencies += "org.scalacheck" % "scalacheck" % "1.5"

ivyPaths := baseDirectory( dir => IvyPaths(dir, Some(dir / "ivy-home"))).value

TaskKey[Unit]("check") := {
  val report = update.value
	val files = report.matching( moduleFilter(organization = "org.scalacheck", name = "scalacheck", revision = "1.5") )
	assert(files.nonEmpty, "ScalaCheck module not found in update report")
	val missing = files.filter(! _.exists)
	assert(missing.isEmpty, "Reported ScalaCheck artifact files don't exist: " + missing.mkString(", "))
}
