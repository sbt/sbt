package lmcoursier.internal

import java.time.Instant

final case class ArtifactLock(
    url: String,
    classifier: Option[String],
    extension: String,
    `type`: String
)

final case class DependencyLock(
    organization: String,
    name: String,
    version: String,
    configuration: String,
    classifier: Option[String],
    `type`: String,
    transitives: Seq[String],
    artifacts: Seq[ArtifactLock]
)

final case class ConfigurationLock(
    name: String,
    dependencies: Seq[DependencyLock]
)

final case class LockFileMetadata(
    sbtVersion: String,
    scalaVersion: Option[String],
    timestamp: Instant
)

final case class LockFileData(
    version: String,
    buildClock: String,
    configurations: Seq[ConfigurationLock],
    metadata: LockFileMetadata
)

object LockFileData {
  val currentVersion = "1.0"
}
