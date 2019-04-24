package lmcoursier.definitions

final case class Dependency(
  module: Module,
  version: String,
  configuration: Configuration,
  exclusions: Set[(Organization, ModuleName)],

  // Maven-specific
  attributes: Attributes,
  optional: Boolean,

  transitive: Boolean
)
