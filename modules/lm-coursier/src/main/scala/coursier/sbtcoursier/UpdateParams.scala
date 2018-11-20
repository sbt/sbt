package coursier.sbtcoursier

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
)
