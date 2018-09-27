scalaVersion := "2.11.8"

libraryDependencies += "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M5"

lazy val check = TaskKey[Unit]("check")

check := {
  val report = com.typesafe.sbt.pgp.PgpKeys.updatePgpSignatures.value
  val configReport = report
    .configurations
    .find { confRep =>
      // .toString required with sbt 1.0 (ConfigRef -> String)
      confRep.configuration.toString == "compile"
    }
    .getOrElse {
      sys.error("No configuration report found for configuration 'compile'")
    }
  val moduleReports = configReport.modules
  val signatures = moduleReports
    .flatMap(_.artifacts)
    .filter(_._1.extension == "jar.asc")
    .map(_._2)
  assert(
    signatures.nonEmpty,
    "No signatures found"
  )
  def isCoursierCachePath(p: File) = {
    val abs = p.getAbsolutePath
    abs.contains("/.coursier/") || // Former cache path
      abs.contains("/coursier/") || // New cache path, Linux
      abs.contains("/Coursier/")  // New cache path, OS X
  }
  assert(
    signatures.forall(isCoursierCachePath),
    s"Found signatures not provided by coursier:\n" +
      signatures
        .filter(!isCoursierCachePath(_))
        .map("  " + _)
        .mkString("\n")
  )
}
