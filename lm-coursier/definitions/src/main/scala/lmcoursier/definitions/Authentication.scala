package lmcoursier.definitions

import dataclass.*

@data class Authentication(
    user: String,
    password: String,
    optional: Boolean = false,
    realmOpt: Option[String] = None,
    @since("1.0")
    headers: Seq[(String, String)] = Nil,
    @since("1.1")
    httpsOnly: Boolean = true,
    @since("1.2")
    passOnRedirect: Boolean = false
) {
  override def toString(): String =
    s"Authentication(user=$user)"
}
