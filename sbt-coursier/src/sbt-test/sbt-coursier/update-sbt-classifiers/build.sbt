
import Compatibility._

scalaVersion := appConfiguration.value.provider.scalaProvider.version

lazy val updateSbtClassifiersCheck = TaskKey[Unit]("updateSbtClassifiersCheck")

updateSbtClassifiersCheck := {

  val configReport = updateSbtClassifiers
    .value
    .configuration(Default)
    .getOrElse {
      throw new Exception(
        "default configuration not found in updateSbtClassifiers report"
      )
    }

  def artifacts(org: String, name: String) = configReport
    .modules
    .collect {
      case moduleReport
        if moduleReport.module.organization == org &&
             moduleReport.module.name == name =>
        moduleReport.artifacts
    }
    .toSeq
    .flatten

  def ensureHasArtifact(org: String, name: String) =
    assert(
      artifacts(org, name).exists(_._2.getName.endsWith("-sources.jar")),
      s"$org:$name not found"
    )

  ensureHasArtifact("org.scala-lang", "scala-library")
  ensureHasArtifact("io.get-coursier", "coursier_" + scalaBinaryVersion.value)
  ensureHasArtifact("io.get-coursier", "sbt-coursier")
}
