package lmcoursier.credentials

import dataclass.*

@data class DirectCredentials(
    host: String = "", // scruff: ignore(no_default_param)
    username: String = "", // scruff: ignore(no_default_param)
    password: String = "", // scruff: ignore(no_default_param)
    @since("1.0")
    realm: Option[String] = None, // scruff: ignore(no_default_param)
    @since("1.1")
    optional: Boolean = true, // scruff: ignore(no_default_param)
    @since("1.2")
    matchHost: Boolean = false, // scruff: ignore(no_default_param)
    @since("1.3")
    httpsOnly: Boolean = true // scruff: ignore(no_default_param)
) extends Credentials:

  override def toString(): String = s"DirectCredentials(host=$host, username=$username)"
