package lmcoursier.credentials

final class DirectCredentials private(
  val host: String,
  val username: String,
  val password: String,
  val realm: Option[String],
  val optional: Boolean,
  val matchHost: Boolean,
  val httpsOnly: Boolean
) extends Credentials {

  private def this() = this("", "", "", None, true, false, true)
  private def this(host: String, username: String, password: String) = this(host, username, password, None, true, false, true)
  private def this(host: String, username: String, password: String, realm: Option[String]) = this(host, username, password, realm, true, false, true)
  private def this(host: String, username: String, password: String, realm: Option[String], optional: Boolean) = this(host, username, password, realm, optional, false, true)

  override def equals(o: Any): Boolean = o match {
    case x: DirectCredentials => (this.host == x.host) && (this.username == x.username) && (this.password == x.password) && (this.realm == x.realm) && (this.optional == x.optional) && (this.matchHost == x.matchHost) && (this.httpsOnly == x.httpsOnly)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "lmcoursier.credentials.DirectCredentials".##) + host.##) + username.##) + password.##) + realm.##) + optional.##) + matchHost.##) + httpsOnly.##)
  }
  override def toString: String = {
    "Credentials(" + host + ", " + username + ", " + "****" + ", " + realm + ", " + optional + ", " + matchHost + ", " + httpsOnly + ")"
  }
  private[this] def copy(host: String = host, username: String = username, password: String = password, realm: Option[String] = realm, optional: Boolean = optional, matchHost: Boolean = matchHost, httpsOnly: Boolean = httpsOnly): DirectCredentials = {
    new DirectCredentials(host, username, password, realm, optional, matchHost, httpsOnly)
  }
  def withHost(host: String): DirectCredentials = {
    copy(host = host)
  }
  def withUsername(username: String): DirectCredentials = {
    copy(username = username)
  }
  def withPassword(password: String): DirectCredentials = {
    copy(password = password)
  }
  def withRealm(realm: Option[String]): DirectCredentials = {
    copy(realm = realm)
  }
  def withRealm(realm: String): DirectCredentials = {
    copy(realm = Option(realm))
  }
  def withOptional(optional: Boolean): DirectCredentials = {
    copy(optional = optional)
  }
  def withMatchHost(matchHost: Boolean): DirectCredentials =
    copy(matchHost = matchHost)
  def withHttpsOnly(httpsOnly: Boolean): DirectCredentials =
    copy(httpsOnly = httpsOnly)
}
object DirectCredentials {

  def apply(): DirectCredentials = new DirectCredentials()
  def apply(host: String, username: String, password: String): DirectCredentials = new DirectCredentials(host, username, password)
  def apply(host: String, username: String, password: String, realm: Option[String]): DirectCredentials = new DirectCredentials(host, username, password, realm)
  def apply(host: String, username: String, password: String, realm: String): DirectCredentials = new DirectCredentials(host, username, password, Option(realm))
  def apply(host: String, username: String, password: String, realm: Option[String], optional: Boolean): DirectCredentials = new DirectCredentials(host, username, password, realm, optional)
  def apply(host: String, username: String, password: String, realm: String, optional: Boolean): DirectCredentials = new DirectCredentials(host, username, password, Option(realm), optional)
}
