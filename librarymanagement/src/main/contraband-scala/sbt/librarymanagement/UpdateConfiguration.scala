/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class UpdateConfiguration private (
  val retrieve: Option[sbt.internal.librarymanagement.RetrieveConfiguration],
  val missingOk: Boolean,
  val logging: sbt.librarymanagement.UpdateLogging,
  val artifactFilter: sbt.librarymanagement.ArtifactTypeFilter,
  val offline: Boolean,
  val frozen: Boolean) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: UpdateConfiguration => (this.retrieve == x.retrieve) && (this.missingOk == x.missingOk) && (this.logging == x.logging) && (this.artifactFilter == x.artifactFilter) && (this.offline == x.offline) && (this.frozen == x.frozen)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "UpdateConfiguration".##) + retrieve.##) + missingOk.##) + logging.##) + artifactFilter.##) + offline.##) + frozen.##)
  }
  override def toString: String = {
    "UpdateConfiguration(" + retrieve + ", " + missingOk + ", " + logging + ", " + artifactFilter + ", " + offline + ", " + frozen + ")"
  }
  protected[this] def copy(retrieve: Option[sbt.internal.librarymanagement.RetrieveConfiguration] = retrieve, missingOk: Boolean = missingOk, logging: sbt.librarymanagement.UpdateLogging = logging, artifactFilter: sbt.librarymanagement.ArtifactTypeFilter = artifactFilter, offline: Boolean = offline, frozen: Boolean = frozen): UpdateConfiguration = {
    new UpdateConfiguration(retrieve, missingOk, logging, artifactFilter, offline, frozen)
  }
  def withRetrieve(retrieve: Option[sbt.internal.librarymanagement.RetrieveConfiguration]): UpdateConfiguration = {
    copy(retrieve = retrieve)
  }
  def withRetrieve(retrieve: sbt.internal.librarymanagement.RetrieveConfiguration): UpdateConfiguration = {
    copy(retrieve = Option(retrieve))
  }
  def withMissingOk(missingOk: Boolean): UpdateConfiguration = {
    copy(missingOk = missingOk)
  }
  def withLogging(logging: sbt.librarymanagement.UpdateLogging): UpdateConfiguration = {
    copy(logging = logging)
  }
  def withArtifactFilter(artifactFilter: sbt.librarymanagement.ArtifactTypeFilter): UpdateConfiguration = {
    copy(artifactFilter = artifactFilter)
  }
  def withOffline(offline: Boolean): UpdateConfiguration = {
    copy(offline = offline)
  }
  def withFrozen(frozen: Boolean): UpdateConfiguration = {
    copy(frozen = frozen)
  }
}
object UpdateConfiguration {
  
  def apply(retrieve: Option[sbt.internal.librarymanagement.RetrieveConfiguration], missingOk: Boolean, logging: sbt.librarymanagement.UpdateLogging, artifactFilter: sbt.librarymanagement.ArtifactTypeFilter, offline: Boolean, frozen: Boolean): UpdateConfiguration = new UpdateConfiguration(retrieve, missingOk, logging, artifactFilter, offline, frozen)
  def apply(retrieve: sbt.internal.librarymanagement.RetrieveConfiguration, missingOk: Boolean, logging: sbt.librarymanagement.UpdateLogging, artifactFilter: sbt.librarymanagement.ArtifactTypeFilter, offline: Boolean, frozen: Boolean): UpdateConfiguration = new UpdateConfiguration(Option(retrieve), missingOk, logging, artifactFilter, offline, frozen)
}
