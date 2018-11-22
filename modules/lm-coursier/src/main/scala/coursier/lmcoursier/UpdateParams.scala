package coursier.lmcoursier

import java.io.File

import coursier.FileError
import coursier.core._

final case class UpdateParams(
  shadedConfigOpt: Option[(String, Configuration)],
  artifacts: Map[Artifact, Either[FileError, File]],
  classifiers: Option[Seq[Classifier]],
  configs: Map[Configuration, Set[Configuration]],
  dependencies: Seq[(Configuration, Dependency)],
  res: Map[Set[Configuration], Resolution],
  ignoreArtifactErrors: Boolean,
  includeSignatures: Boolean,
  sbtBootJarOverrides: Map[(Module, String), File]
) {

  lazy val artifactFiles = artifacts.collect {
    case (artifact, Right(file)) =>
      artifact -> file
  }

  // can be non empty only if ignoreArtifactErrors is true or some optional artifacts are not found
  lazy val erroredArtifacts = artifacts
    .collect {
      case (artifact, Left(_)) =>
        artifact
    }
    .toSet

  def artifactFileOpt(
    module: Module,
    version: String,
    attributes: Attributes,
    artifact: Artifact
  ): Option[File] = {

    // Under some conditions, SBT puts the scala JARs of its own classpath
    // in the application classpath. Ensuring we return SBT's jars rather than
    // JARs from the coursier cache, so that a same JAR doesn't land twice in the
    // application classpath (once via SBT jars, once via coursier cache).
    val fromBootJars =
      if (attributes.classifier.isEmpty && attributes.`type` == Type.jar)
        sbtBootJarOverrides.get((module, version))
      else
        None

    val res = fromBootJars.orElse(artifactFiles.get(artifact))

    if (res.isEmpty && !erroredArtifacts(artifact))
      sys.error(s"${artifact.url} not downloaded (should not happen)")

    res
  }

}
