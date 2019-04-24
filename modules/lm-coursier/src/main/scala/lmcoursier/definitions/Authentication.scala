package lmcoursier.definitions

final case class Authentication(
  user: String,
  password: String,
  optional: Boolean = false,
  realmOpt: Option[String] = None
) {
  override def toString: String =
    s"Authentication($user, *******, $optional, $realmOpt)"
}
