package coursier.lmcoursier

import coursier.cache.{CacheLogger, FileCache}
import coursier.core.{Classifier, Resolution}
import coursier.util.Task

final case class ArtifactsParams(
  classifiers: Option[Seq[Classifier]],
  resolutions: Seq[Resolution],
  includeSignatures: Boolean,
  loggerOpt: Option[CacheLogger],
  projectName: String,
  sbtClassifiers: Boolean,
  cache: FileCache[Task],
  parallel: Int
)
