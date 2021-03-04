/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol
/**
 * This file should exist throughout the lifetime of the server.
 * It can be used to find out the transport protocol (port number etc).
 * @param uri URI of the sbt server.
 */
final class PortFile private (
  val uri: String,
  val tokenfilePath: Option[String],
  val tokenfileUri: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: PortFile => (this.uri == x.uri) && (this.tokenfilePath == x.tokenfilePath) && (this.tokenfileUri == x.tokenfileUri)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.protocol.PortFile".##) + uri.##) + tokenfilePath.##) + tokenfileUri.##)
  }
  override def toString: String = {
    "PortFile(" + uri + ", " + tokenfilePath + ", " + tokenfileUri + ")"
  }
  private[this] def copy(uri: String = uri, tokenfilePath: Option[String] = tokenfilePath, tokenfileUri: Option[String] = tokenfileUri): PortFile = {
    new PortFile(uri, tokenfilePath, tokenfileUri)
  }
  def withUri(uri: String): PortFile = {
    copy(uri = uri)
  }
  def withTokenfilePath(tokenfilePath: Option[String]): PortFile = {
    copy(tokenfilePath = tokenfilePath)
  }
  def withTokenfilePath(tokenfilePath: String): PortFile = {
    copy(tokenfilePath = Option(tokenfilePath))
  }
  def withTokenfileUri(tokenfileUri: Option[String]): PortFile = {
    copy(tokenfileUri = tokenfileUri)
  }
  def withTokenfileUri(tokenfileUri: String): PortFile = {
    copy(tokenfileUri = Option(tokenfileUri))
  }
}
object PortFile {
  
  def apply(uri: String, tokenfilePath: Option[String], tokenfileUri: Option[String]): PortFile = new PortFile(uri, tokenfilePath, tokenfileUri)
  def apply(uri: String, tokenfilePath: String, tokenfileUri: String): PortFile = new PortFile(uri, Option(tokenfilePath), Option(tokenfileUri))
}
