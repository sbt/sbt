package coursier.lmcoursier

import coursier.cache.CacheLogger
import coursier.core.{Classifier, Resolution}
import coursier.params.CacheParams

final case class ArtifactsParams(
  classifiers: Option[Seq[Classifier]],
  resolutions: Seq[Resolution],
  includeSignatures: Boolean,
  loggerOpt: Option[CacheLogger],
  projectName: String,
  sbtClassifiers: Boolean,
  cacheParams: CacheParams
)
