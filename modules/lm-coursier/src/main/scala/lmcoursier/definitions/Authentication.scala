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
    withPassword("****")
      .withHeaders(
        headers.map {
          case (k, v) => (k, "****")
        }
      )
      .productIterator
      .mkString("Authentication(", ", ", ")")
}

object Authentication {

  def apply(headers: Seq[(String, String)]): Authentication =
    Authentication("", "").withHeaders(headers)
}
