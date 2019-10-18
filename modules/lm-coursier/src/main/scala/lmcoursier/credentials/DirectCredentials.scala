package lmcoursier.credentials

import dataclass._

@data class DirectCredentials(
  host: String = "",
  username: String = "",
  password: String = "",
  @since
  realm: Option[String] = None,
  @since
  optional: Boolean = true,
  @since
  matchHost: Boolean = false,
  httpsOnly: Boolean = true
) extends Credentials {

  def withRealm(realm: String): DirectCredentials =
    withRealm(Option(realm))

  override def toString(): String =
    withPassword("****")
      .productIterator
      .mkString("DirectCredentials(", ", ", ")")
}

object DirectCredentials {
  def apply(host: String, username: String, password: String, realm: String): DirectCredentials = DirectCredentials(host, username, password, Option(realm))
  def apply(host: String, username: String, password: String, realm: String, optional: Boolean): DirectCredentials = DirectCredentials(host, username, password, Option(realm), optional)
}
