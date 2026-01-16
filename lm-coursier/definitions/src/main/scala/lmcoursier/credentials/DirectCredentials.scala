package lmcoursier.credentials

import dataclass.*

@data class DirectCredentials(
    host: String = "",
    username: String = "",
    password: String = "",
    @since("1.0")
    realm: Option[String] = None,
    @since("1.1")
    optional: Boolean = true,
    @since("1.2")
    matchHost: Boolean = false,
    @since("1.3")
    httpsOnly: Boolean = true
) extends Credentials {

  override def toString(): String = s"DirectCredentials(host=$host, username=$username)"
}
