ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

libraryDependencies += "org.scalacheck" % "scalacheck" % "1.5"

TaskKey[Unit]("check") := {
  val report = update.value
  val files = report.matching( moduleFilter(organization = "org.scalacheck", name = "scalacheck", revision = "1.5") )
  assert(files.nonEmpty, "ScalaCheck module not found in update report")
  val missing = files.filter(! _.exists)
  assert(missing.isEmpty, "Reported ScalaCheck artifact files don't exist: " + missing.mkString(", "))
}
