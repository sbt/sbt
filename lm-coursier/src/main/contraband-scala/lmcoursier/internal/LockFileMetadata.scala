/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal
final class LockFileMetadata private (
  val sbtVersion: String,
  val scalaVersion: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: LockFileMetadata => (this.sbtVersion == x.sbtVersion) && (this.scalaVersion == x.scalaVersion)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "lmcoursier.internal.LockFileMetadata".##) + sbtVersion.##) + scalaVersion.##)
  }
  override def toString: String = {
    "LockFileMetadata(" + sbtVersion + ", " + scalaVersion + ")"
  }
  private def copy(sbtVersion: String = sbtVersion, scalaVersion: Option[String] = scalaVersion): LockFileMetadata = {
    new LockFileMetadata(sbtVersion, scalaVersion)
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
}
object LockFileMetadata {
  
  def apply(sbtVersion: String, scalaVersion: Option[String]): LockFileMetadata = new LockFileMetadata(sbtVersion, scalaVersion)
  def apply(sbtVersion: String, scalaVersion: String): LockFileMetadata = new LockFileMetadata(sbtVersion, Option(scalaVersion))
}
