lazy val check = taskKey[Unit]("Runs the check")

scalaVersion := "2.13.10"
platform := Platform.sjs1

// By default platformOpt field is set to None
// Given %% lm engines will sustitute it with the subproject's platform suffix on `update`
libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % "4.1.0",
  "junit" % "junit" % "4.13.1",
)

TaskKey[Unit]("check") := {
  val ur = update.value
  val files = ur.matching(moduleFilter(organization = "com.github.scopt", name = "scopt_sjs1_2.13", revision = "*"))
  assert(files.nonEmpty, s"sjs1 scopt module was not found in update report: $ur")

  val files2 = ur.matching(moduleFilter(organization = "junit", name = "junit", revision = "*"))
  assert(files2.nonEmpty, s"junit module was not found in update report: $ur")
}
csrCacheDirectory := baseDirectory.value / "coursier-cache"
