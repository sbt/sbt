package lmcoursier.definitions
import dataclass.data

@data class Module(
  organization: Organization,
  name: ModuleName,
  attributes: Map[String, String]
)
