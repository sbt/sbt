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
  /** URL of the sbt server. */
  val url: String,
  val tokenfile: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: PortFile => (this.url == x.url) && (this.tokenfile == x.tokenfile)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.protocol.PortFile".##) + url.##) + tokenfile.##)
  }
  override def toString: String = {
    "PortFile(" + url + ", " + tokenfile + ")"
  }
  protected[this] def copy(url: String = url, tokenfile: Option[String] = tokenfile): PortFile = {
    new PortFile(url, tokenfile)
  }
  def withUrl(url: String): PortFile = {
    copy(url = url)
  }
  def withTokenfile(tokenfile: Option[String]): PortFile = {
    copy(tokenfile = tokenfile)
  }
  def withTokenfile(tokenfile: String): PortFile = {
    copy(tokenfile = Option(tokenfile))
  }
}
object PortFile {
  
  def apply(url: String, tokenfile: Option[String]): PortFile = new PortFile(url, tokenfile)
  def apply(url: String, tokenfile: String): PortFile = new PortFile(url, Option(tokenfile))
}
