/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** @param uri The target's Uri */
final class DebugSessionAddress private (
  val uri: java.net.URI) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: DebugSessionAddress => (this.uri == x.uri)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.DebugSessionAddress".##) + uri.##)
  }
  override def toString: String = {
    "DebugSessionAddress(" + uri + ")"
  }
  private[this] def copy(uri: java.net.URI = uri): DebugSessionAddress = {
    new DebugSessionAddress(uri)
  }
  def withUri(uri: java.net.URI): DebugSessionAddress = {
    copy(uri = uri)
  }
}
object DebugSessionAddress {
  
  def apply(uri: java.net.URI): DebugSessionAddress = new DebugSessionAddress(uri)
}
