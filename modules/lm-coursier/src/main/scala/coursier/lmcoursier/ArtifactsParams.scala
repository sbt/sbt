package coursier.lmcoursier

import coursier.cache.CacheLogger
import coursier.core.{Classifier, Resolution}
import coursier.params.CacheParams

final case class ArtifactsParams(
  classifiers: Option[Seq[Classifier]],
  res: Seq[Resolution],
  includeSignatures: Boolean,
  logger: CacheLogger,
  projectName: String,
  sbtClassifiers: Boolean,
  cacheParams: CacheParams
)
