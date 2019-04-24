package lmcoursier.definitions

final case class Module(
  organization: Organization,
  name: ModuleName,
  attributes: Map[String, String]
)
