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

  override def toString(): String =
    withPassword("****")
      .productIterator
      .mkString("DirectCredentials(", ", ", ")")
}
