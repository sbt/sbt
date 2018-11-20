package coursier.lmcoursier

import java.io.File

import coursier.{Cache, CachePolicy}
import coursier.core.{Classifier, Resolution}

import scala.concurrent.duration.Duration

final case class ArtifactsParams(
  classifiers: Option[Seq[Classifier]],
  res: Seq[Resolution],
  includeSignatures: Boolean,
  parallelDownloads: Int,
  createLogger: () => Cache.Logger,
  cache: File,
  artifactsChecksums: Seq[Option[String]],
  ttl: Option[Duration],
  cachePolicies: Seq[CachePolicy],
  projectName: String,
  sbtClassifiers: Boolean
)
