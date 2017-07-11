/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** Basic SCM information for a project module */
final class ScmInfo private (
  val browseUrl: java.net.URL,
  val connection: String,
  val devConnection: Option[String]) extends Serializable {
  
  private def this(browseUrl: java.net.URL, connection: String) = this(browseUrl, connection, None)
  
  override def equals(o: Any): Boolean = o match {
    case x: ScmInfo => (this.browseUrl == x.browseUrl) && (this.connection == x.connection) && (this.devConnection == x.devConnection)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "ScmInfo".##) + browseUrl.##) + connection.##) + devConnection.##)
  }
  override def toString: String = {
    "ScmInfo(" + browseUrl + ", " + connection + ", " + devConnection + ")"
  }
  protected[this] def copy(browseUrl: java.net.URL = browseUrl, connection: String = connection, devConnection: Option[String] = devConnection): ScmInfo = {
    new ScmInfo(browseUrl, connection, devConnection)
  }
  def withBrowseUrl(browseUrl: java.net.URL): ScmInfo = {
    copy(browseUrl = browseUrl)
  }
  def withConnection(connection: String): ScmInfo = {
    copy(connection = connection)
  }
  def withDevConnection(devConnection: Option[String]): ScmInfo = {
    copy(devConnection = devConnection)
  }
  def withDevConnection(devConnection: String): ScmInfo = {
    copy(devConnection = Option(devConnection))
  }
}
object ScmInfo {
  
  def apply(browseUrl: java.net.URL, connection: String): ScmInfo = new ScmInfo(browseUrl, connection, None)
  def apply(browseUrl: java.net.URL, connection: String, devConnection: Option[String]): ScmInfo = new ScmInfo(browseUrl, connection, devConnection)
  def apply(browseUrl: java.net.URL, connection: String, devConnection: String): ScmInfo = new ScmInfo(browseUrl, connection, Option(devConnection))
}
