/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol
/**
 * This file should exist throughout the lifetime of the server.
 * It can be used to find out the transport protocol (port number etc).
 */
final class PortFile private (
  /** URI of the sbt server. */
  val uri: String,
  val tokenfile: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: PortFile => (this.uri == x.uri) && (this.tokenfile == x.tokenfile)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.protocol.PortFile".##) + uri.##) + tokenfile.##)
  }
  override def toString: String = {
    "PortFile(" + uri + ", " + tokenfile + ")"
  }
  protected[this] def copy(uri: String = uri, tokenfile: Option[String] = tokenfile): PortFile = {
    new PortFile(uri, tokenfile)
  }
  def withUri(uri: String): PortFile = {
    copy(uri = uri)
  }
  def withTokenfile(tokenfile: Option[String]): PortFile = {
    copy(tokenfile = tokenfile)
  }
  def withTokenfile(tokenfile: String): PortFile = {
    copy(tokenfile = Option(tokenfile))
  }
}
object PortFile {
  
  def apply(uri: String, tokenfile: Option[String]): PortFile = new PortFile(uri, tokenfile)
  def apply(uri: String, tokenfile: String): PortFile = new PortFile(uri, Option(tokenfile))
}
