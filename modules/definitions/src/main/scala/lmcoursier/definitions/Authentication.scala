package lmcoursier.definitions

import dataclass._

@data class Authentication(
  user: String,
  password: String,
  optional: Boolean = false,
  realmOpt: Option[String] = None,
  @since
  headers: Seq[(String,String)] = Nil,
  httpsOnly: Boolean = true,
  passOnRedirect: Boolean = false
) {
  override def toString(): String =
    s"Authentication(user=$user)"
}
