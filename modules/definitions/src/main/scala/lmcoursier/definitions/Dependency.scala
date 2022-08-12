package lmcoursier.definitions

import dataclass.data

@data class Dependency(
  module: Module,
  version: String,
  configuration: Configuration,
  exclusions: Set[(Organization, ModuleName)],
  publication: Publication,
  optional: Boolean,
  transitive: Boolean
)
