package coursier.lmcoursier

import java.io.File

import coursier.cache.CacheLogger
import coursier.{Cache, CachePolicy}
import coursier.core.{Classifier, Resolution}

import scala.concurrent.duration.Duration

final case class ArtifactsParams(
  classifiers: Option[Seq[Classifier]],
  res: Seq[Resolution],
  includeSignatures: Boolean,
  parallelDownloads: Int,
  createLogger: () => CacheLogger,
  cache: File,
  artifactsChecksums: Seq[Option[String]],
  ttl: Option[Duration],
  cachePolicies: Seq[CachePolicy],
  projectName: String,
  sbtClassifiers: Boolean
)
