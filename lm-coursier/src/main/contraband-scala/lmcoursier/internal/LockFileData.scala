/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal
final class LockFileData private (
  val version: String,
  val requested: lmcoursier.internal.RequestedInputs,
  val configurations: Vector[lmcoursier.internal.ConfigurationLock],
  val metadata: lmcoursier.internal.LockFileMetadata) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: LockFileData => (this.version == x.version) && (this.requested == x.requested) && (this.configurations == x.configurations) && (this.metadata == x.metadata)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "lmcoursier.internal.LockFileData".##) + version.##) + requested.##) + configurations.##) + metadata.##)
  }
  override def toString: String = {
    "LockFileData(" + version + ", " + requested + ", " + configurations + ", " + metadata + ")"
  }
  private def copy(version: String = version, requested: lmcoursier.internal.RequestedInputs = requested, configurations: Vector[lmcoursier.internal.ConfigurationLock] = configurations, metadata: lmcoursier.internal.LockFileMetadata = metadata): LockFileData = {
    new LockFileData(version, requested, configurations, metadata)
  }
  def withVersion(version: String): LockFileData = {
    copy(version = version)
  }
  def withRequested(requested: lmcoursier.internal.RequestedInputs): LockFileData = {
    copy(requested = requested)
  }
  def withConfigurations(configurations: Vector[lmcoursier.internal.ConfigurationLock]): LockFileData = {
    copy(configurations = configurations)
  }
  def withMetadata(metadata: lmcoursier.internal.LockFileMetadata): LockFileData = {
    copy(metadata = metadata)
  }
}
object LockFileData {
  
  def apply(version: String, requested: lmcoursier.internal.RequestedInputs, configurations: Vector[lmcoursier.internal.ConfigurationLock], metadata: lmcoursier.internal.LockFileMetadata): LockFileData = new LockFileData(version, requested, configurations, metadata)
}
