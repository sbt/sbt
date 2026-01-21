/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal
final class LockFileMetadata private (
  val sbtVersion: String,
  val scalaVersion: Option[String],
  val timestamp: java.time.Instant) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: LockFileMetadata => (this.sbtVersion == x.sbtVersion) && (this.scalaVersion == x.scalaVersion) && (this.timestamp == x.timestamp)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "lmcoursier.internal.LockFileMetadata".##) + sbtVersion.##) + scalaVersion.##) + timestamp.##)
  }
  override def toString: String = {
    "LockFileMetadata(" + sbtVersion + ", " + scalaVersion + ", " + timestamp + ")"
  }
  private def copy(sbtVersion: String = sbtVersion, scalaVersion: Option[String] = scalaVersion, timestamp: java.time.Instant = timestamp): LockFileMetadata = {
    new LockFileMetadata(sbtVersion, scalaVersion, timestamp)
  }
  def withSbtVersion(sbtVersion: String): LockFileMetadata = {
    copy(sbtVersion = sbtVersion)
  }
  def withScalaVersion(scalaVersion: Option[String]): LockFileMetadata = {
    copy(scalaVersion = scalaVersion)
  }
  def withScalaVersion(scalaVersion: String): LockFileMetadata = {
    copy(scalaVersion = Option(scalaVersion))
  }
  def withTimestamp(timestamp: java.time.Instant): LockFileMetadata = {
    copy(timestamp = timestamp)
  }
}
object LockFileMetadata {
  
  def apply(sbtVersion: String, scalaVersion: Option[String], timestamp: java.time.Instant): LockFileMetadata = new LockFileMetadata(sbtVersion, scalaVersion, timestamp)
  def apply(sbtVersion: String, scalaVersion: String, timestamp: java.time.Instant): LockFileMetadata = new LockFileMetadata(sbtVersion, Option(scalaVersion), timestamp)
}
