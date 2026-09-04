/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol
/**
 * This file should exist throughout the lifetime of the server.
 * It can be used to find out the transport protocol (port number etc).
 * @param uri URI of the sbt server.
 * @param sysProps The -D options the thin client passed to this server, as names and salted digests.
 */
final class PortFile private (
  val uri: String,
  val tokenfilePath: Option[String],
  val tokenfileUri: Option[String],
  val sysProps: Vector[String]) extends Serializable {
  
  private def this(uri: String, tokenfilePath: Option[String], tokenfileUri: Option[String]) = this(uri, tokenfilePath, tokenfileUri, Vector())
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: PortFile => (this.uri == x.uri) && (this.tokenfilePath == x.tokenfilePath) && (this.tokenfileUri == x.tokenfileUri) && (this.sysProps == x.sysProps)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.protocol.PortFile".##) + uri.##) + tokenfilePath.##) + tokenfileUri.##) + sysProps.##)
  }
  override def toString: String = {
    "PortFile(" + uri + ", " + tokenfilePath + ", " + tokenfileUri + ", " + sysProps + ")"
  }
  private def copy(uri: String = uri, tokenfilePath: Option[String] = tokenfilePath, tokenfileUri: Option[String] = tokenfileUri, sysProps: Vector[String] = sysProps): PortFile = {
    new PortFile(uri, tokenfilePath, tokenfileUri, sysProps)
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
  def withSysProps(sysProps: Vector[String]): PortFile = {
    copy(sysProps = sysProps)
  }
}
object PortFile {
  
  def apply(uri: String, tokenfilePath: Option[String], tokenfileUri: Option[String]): PortFile = new PortFile(uri, tokenfilePath, tokenfileUri)
  def apply(uri: String, tokenfilePath: String, tokenfileUri: String): PortFile = new PortFile(uri, Option(tokenfilePath), Option(tokenfileUri))
  def apply(uri: String, tokenfilePath: Option[String], tokenfileUri: Option[String], sysProps: Vector[String]): PortFile = new PortFile(uri, tokenfilePath, tokenfileUri, sysProps)
  def apply(uri: String, tokenfilePath: String, tokenfileUri: String, sysProps: Vector[String]): PortFile = new PortFile(uri, Option(tokenfilePath), Option(tokenfileUri), sysProps)
}
