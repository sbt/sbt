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
 * @param sysPropsRecorded Whether sysProps is the whole story. Absent on a server no thin client started, whose
                           options are its own business and unknown here.
 */
final class PortFile private (
  val uri: String,
  val tokenfilePath: Option[String],
  val tokenfileUri: Option[String],
  val sysProps: Vector[String],
  val sysPropsRecorded: Option[Boolean]) extends Serializable {
  
  private def this(uri: String, tokenfilePath: Option[String], tokenfileUri: Option[String]) = this(uri, tokenfilePath, tokenfileUri, Vector(), None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: PortFile => (this.uri == x.uri) && (this.tokenfilePath == x.tokenfilePath) && (this.tokenfileUri == x.tokenfileUri) && (this.sysProps == x.sysProps) && (this.sysPropsRecorded == x.sysPropsRecorded)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.protocol.PortFile".##) + uri.##) + tokenfilePath.##) + tokenfileUri.##) + sysProps.##) + sysPropsRecorded.##)
  }
  override def toString: String = {
    "PortFile(" + uri + ", " + tokenfilePath + ", " + tokenfileUri + ", " + sysProps + ", " + sysPropsRecorded + ")"
  }
  private def copy(uri: String = uri, tokenfilePath: Option[String] = tokenfilePath, tokenfileUri: Option[String] = tokenfileUri, sysProps: Vector[String] = sysProps, sysPropsRecorded: Option[Boolean] = sysPropsRecorded): PortFile = {
    new PortFile(uri, tokenfilePath, tokenfileUri, sysProps, sysPropsRecorded)
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
  def withSysPropsRecorded(sysPropsRecorded: Option[Boolean]): PortFile = {
    copy(sysPropsRecorded = sysPropsRecorded)
  }
  def withSysPropsRecorded(sysPropsRecorded: Boolean): PortFile = {
    copy(sysPropsRecorded = Option(sysPropsRecorded))
  }
}
object PortFile {
  
  def apply(uri: String, tokenfilePath: Option[String], tokenfileUri: Option[String]): PortFile = new PortFile(uri, tokenfilePath, tokenfileUri)
  def apply(uri: String, tokenfilePath: String, tokenfileUri: String): PortFile = new PortFile(uri, Option(tokenfilePath), Option(tokenfileUri))
  def apply(uri: String, tokenfilePath: Option[String], tokenfileUri: Option[String], sysProps: Vector[String], sysPropsRecorded: Option[Boolean]): PortFile = new PortFile(uri, tokenfilePath, tokenfileUri, sysProps, sysPropsRecorded)
  def apply(uri: String, tokenfilePath: String, tokenfileUri: String, sysProps: Vector[String], sysPropsRecorded: Boolean): PortFile = new PortFile(uri, Option(tokenfilePath), Option(tokenfileUri), sysProps, Option(sysPropsRecorded))
}
