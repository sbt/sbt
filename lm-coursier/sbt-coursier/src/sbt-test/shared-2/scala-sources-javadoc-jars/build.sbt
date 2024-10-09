
scalaVersion := appConfiguration.value.provider.scalaProvider.version

lazy val updateClassifiersCheck = TaskKey[Unit]("updateClassifiersCheck")

updateClassifiersCheck := {

  val configReport = updateClassifiers.value
    .configuration(Compile)
    .getOrElse {
      throw new Exception(
        "compile configuration not found in updateClassifiers report"
      )
    }

  val scalaLibraryArtifacts = configReport
    .modules
    .collectFirst {
      case moduleReport
        if moduleReport.module.organization == "org.scala-lang" &&
             moduleReport.module.name == "scala-library" =>
        moduleReport.artifacts
    }
    .toSeq
    .flatten

  def classifierArtifact(classifier: String) =
    scalaLibraryArtifacts.collectFirst {
      case (a, _) if a.classifier == Some(classifier) => a
    }

  def ensureHasClassifierArtifact(classifier: String) =
    assert(
      classifierArtifact(classifier).nonEmpty,
      s"scala-library $classifier not found"
    )

  ensureHasClassifierArtifact("javadoc")
  ensureHasClassifierArtifact("sources")
}
