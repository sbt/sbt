scalaVersion := "2.11.8"

libraryDependencies += "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M5"

lazy val check = TaskKey[Unit]("check")

check := {
  val report = com.typesafe.sbt.pgp.PgpKeys.updatePgpSignatures.value
  val configReport = report
    .configurations
    .find { confRep =>
      confRep.configuration == "compile"
    }
    .getOrElse {
      sys.error("No configuration report found for configuration 'compile'")
    }
  val moduleReports = configReport.modules
  val artifacts = moduleReports.flatMap(_.artifacts.map(_._1))
  val signatures = moduleReports
    .flatMap(_.artifacts)
    .filter(_._1.extension == "jar.asc")
    .map(_._2)
  assert(
    signatures.nonEmpty,
    "No signatures found"
  )
  assert(
    signatures.forall(_.getAbsolutePath.contains("/.coursier/cache/")),
    s"Found signatures not provided by coursier:\n${signatures.map("  " + _).mkString("\n")}"
  )
}
