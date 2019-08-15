package lmcoursier.internal

import java.io.File

import coursier.core._
import coursier.util.Artifact

// private[coursier]
final case class UpdateParams(
  shadedConfigOpt: Option[(String, Configuration)],
  artifacts: Map[Artifact, File],
  classifiers: Option[Seq[Classifier]],
  configs: Map[Configuration, Set[Configuration]],
  dependencies: Seq[(Configuration, Dependency)],
  res: Map[Set[Configuration], Resolution],
  includeSignatures: Boolean,
  sbtBootJarOverrides: Map[(Module, String), File]
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

    fromBootJars.orElse(artifacts.get(artifact))
  }

}
