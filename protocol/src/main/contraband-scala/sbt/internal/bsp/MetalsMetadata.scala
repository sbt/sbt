/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Metals metadata in the initialization request
 * @param semanticdbVersion The semanticdb plugin version that should be enabled for Metals code navigation
 * @param supportedScalaVersions The list of scala versions that are supported by Metals
 */
final class MetalsMetadata private (
  val semanticdbVersion: String,
  val supportedScalaVersions: Vector[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: MetalsMetadata => (this.semanticdbVersion == x.semanticdbVersion) && (this.supportedScalaVersions == x.supportedScalaVersions)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.MetalsMetadata".##) + semanticdbVersion.##) + supportedScalaVersions.##)
  }
  override def toString: String = {
    "MetalsMetadata(" + semanticdbVersion + ", " + supportedScalaVersions + ")"
  }
  private[this] def copy(semanticdbVersion: String = semanticdbVersion, supportedScalaVersions: Vector[String] = supportedScalaVersions): MetalsMetadata = {
    new MetalsMetadata(semanticdbVersion, supportedScalaVersions)
  }
  def withSemanticdbVersion(semanticdbVersion: String): MetalsMetadata = {
    copy(semanticdbVersion = semanticdbVersion)
  }
  def withSupportedScalaVersions(supportedScalaVersions: Vector[String]): MetalsMetadata = {
    copy(supportedScalaVersions = supportedScalaVersions)
  }
}
object MetalsMetadata {
  
  def apply(semanticdbVersion: String, supportedScalaVersions: Vector[String]): MetalsMetadata = new MetalsMetadata(semanticdbVersion, supportedScalaVersions)
}
