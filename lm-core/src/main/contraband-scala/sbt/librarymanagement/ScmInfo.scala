/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** Basic SCM information for a project module */
final class ScmInfo private (
  val browseUrl: java.net.URI,
  val connection: String,
  val devConnection: Option[String]) extends Serializable {
  
  private def this(browseUrl: java.net.URI, connection: String) = this(browseUrl, connection, None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScmInfo => (this.browseUrl == x.browseUrl) && (this.connection == x.connection) && (this.devConnection == x.devConnection)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.ScmInfo".##) + browseUrl.##) + connection.##) + devConnection.##)
  }
  override def toString: String = {
    "ScmInfo(" + browseUrl + ", " + connection + ", " + devConnection + ")"
  }
  private[this] def copy(browseUrl: java.net.URI = browseUrl, connection: String = connection, devConnection: Option[String] = devConnection): ScmInfo = {
    new ScmInfo(browseUrl, connection, devConnection)
  }
  def withBrowseUrl(browseUrl: java.net.URI): ScmInfo = {
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
  
  def apply(browseUrl: java.net.URI, connection: String): ScmInfo = new ScmInfo(browseUrl, connection)
  def apply(browseUrl: java.net.URI, connection: String, devConnection: Option[String]): ScmInfo = new ScmInfo(browseUrl, connection, devConnection)
  def apply(browseUrl: java.net.URI, connection: String, devConnection: String): ScmInfo = new ScmInfo(browseUrl, connection, Option(devConnection))
}
