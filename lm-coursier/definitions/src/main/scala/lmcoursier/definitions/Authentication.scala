package lmcoursier.definitions

import dataclass.*

@data class Authentication(
    user: String,
    password: String,
    optional: Boolean = false, // scruff: ignore(no_default_param)
    realmOpt: Option[String] = None, // scruff: ignore(no_default_param)
    @since("1.0")
    headers: Seq[(String, String)] = Nil, // scruff: ignore(no_default_param)
    @since("1.1")
    httpsOnly: Boolean = true, // scruff: ignore(no_default_param)
    @since("1.2")
    passOnRedirect: Boolean = false // scruff: ignore(no_default_param)
):
  override def toString(): String =
    s"Authentication(user=$user)"
