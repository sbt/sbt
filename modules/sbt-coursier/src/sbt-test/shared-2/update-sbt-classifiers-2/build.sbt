
scalaVersion := appConfiguration.value.provider.scalaProvider.version

lazy val updateSbtClassifiersCheck = TaskKey[Unit]("updateSbtClassifiersCheck")

updateSbtClassifiersCheck := {

  val defaultModules = updateSbtClassifiers
    .value
    .configuration(Default)
    .map(_.modules)
    .getOrElse(Nil)

  val compileModules = updateSbtClassifiers
    .value
    .configuration(Compile)
    .map(_.modules)
    .getOrElse(Nil)

  def artifacts(org: String, name: String) =
    (defaultModules ++ compileModules)
      .map { m =>
        println(s"Found module $m")
        m
      }
      .collect {
        case moduleReport
          if moduleReport.module.organization == org &&
               moduleReport.module.name == name =>
          moduleReport.artifacts
      }
      .toSeq
      .flatten

  def ensureHasArtifact(orgName: (String, String)*) =
    assert(
      orgName.exists {
        case (org, name) =>
          artifacts(org, name).exists(_._2.getName.endsWith("-sources.jar"))
      },
      s"Any of $orgName not found"
    )

  ensureHasArtifact("org.scala-lang" -> "scala-library")
  ensureHasArtifact("org.scala-lang.modules" -> s"scala-xml_${scalaBinaryVersion.value}")
  ensureHasArtifact(
    "io.get-coursier" -> s"lm-coursier_${scalaBinaryVersion.value}",
    "io.get-coursier" -> s"lm-coursier-shaded_${scalaBinaryVersion.value}"
  )
}
