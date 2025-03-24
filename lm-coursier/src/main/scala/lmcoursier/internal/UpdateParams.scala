package lmcoursier.internal

import java.io.File

import coursier.core.*
import coursier.util.Artifact

// private[coursier]
final case class UpdateParams(
    thisModule: (Module, String),
    artifacts: Map[Artifact, File],
    fullArtifacts: Option[Map[(Dependency, Publication, Artifact), Option[File]]],
    classifiers: Option[Seq[Classifier]],
    configs: Map[Configuration, Set[Configuration]],
    dependencies: Seq[(Configuration, Dependency)],
    forceVersions: Map[Module, String],
    interProjectDependencies: Seq[Project],
    res: Map[Configuration, Resolution],
    includeSignatures: Boolean,
    sbtBootJarOverrides: Map[(Module, String), File],
    classpathOrder: Boolean,
    missingOk: Boolean,
    classLoaders: Seq[ClassLoader]
) {

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

    val artifact0 =
      if (missingOk) artifact.withOptional(true)
      else artifact

    fromBootJars.orElse(artifacts.get(artifact0))
  }

}
