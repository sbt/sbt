/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Build Target Identifier
 * @param uri The target's Uri
 */
final class BuildTargetIdentifier private (
  val uri: java.net.URI) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: BuildTargetIdentifier => (this.uri == x.uri)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.BuildTargetIdentifier".##) + uri.##)
  }
  override def toString: String = {
    "BuildTargetIdentifier(" + uri + ")"
  }
  private[this] def copy(uri: java.net.URI = uri): BuildTargetIdentifier = {
    new BuildTargetIdentifier(uri)
  }
  def withUri(uri: java.net.URI): BuildTargetIdentifier = {
    copy(uri = uri)
  }
}
object BuildTargetIdentifier {
  
  def apply(uri: java.net.URI): BuildTargetIdentifier = new BuildTargetIdentifier(uri)
}
