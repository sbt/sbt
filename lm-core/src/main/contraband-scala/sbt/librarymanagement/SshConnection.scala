/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class SshConnection private (
  val authentication: Option[sbt.librarymanagement.SshAuthentication],
  val hostname: Option[String],
  val port: Option[Int]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: SshConnection => (this.authentication == x.authentication) && (this.hostname == x.hostname) && (this.port == x.port)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.SshConnection".##) + authentication.##) + hostname.##) + port.##)
  }
  override def toString: String = {
    "SshConnection(" + authentication + ", " + hostname + ", " + port + ")"
  }
  private[this] def copy(authentication: Option[sbt.librarymanagement.SshAuthentication] = authentication, hostname: Option[String] = hostname, port: Option[Int] = port): SshConnection = {
    new SshConnection(authentication, hostname, port)
  }
  def withAuthentication(authentication: Option[sbt.librarymanagement.SshAuthentication]): SshConnection = {
    copy(authentication = authentication)
  }
  def withAuthentication(authentication: sbt.librarymanagement.SshAuthentication): SshConnection = {
    copy(authentication = Option(authentication))
  }
  def withHostname(hostname: Option[String]): SshConnection = {
    copy(hostname = hostname)
  }
  def withHostname(hostname: String): SshConnection = {
    copy(hostname = Option(hostname))
  }
  def withPort(port: Option[Int]): SshConnection = {
    copy(port = port)
  }
  def withPort(port: Int): SshConnection = {
    copy(port = Option(port))
  }
}
object SshConnection {
  
  def apply(authentication: Option[sbt.librarymanagement.SshAuthentication], hostname: Option[String], port: Option[Int]): SshConnection = new SshConnection(authentication, hostname, port)
  def apply(authentication: sbt.librarymanagement.SshAuthentication, hostname: String, port: Int): SshConnection = new SshConnection(Option(authentication), Option(hostname), Option(port))
}
