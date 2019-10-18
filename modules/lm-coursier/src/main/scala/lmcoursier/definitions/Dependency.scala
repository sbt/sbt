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
) {
  def attributes: Attributes = publication.attributes
  def withAttributes(attributes: Attributes): Dependency =
    withPublication(
      publication
        .withType(attributes.`type`)
        .withClassifier(attributes.classifier)
    )
}

object Dependency {
  def apply(
    module: Module,
    version: String,
    configuration: Configuration,
    exclusions: Set[(Organization, ModuleName)],
    attributes: Attributes,
    optional: Boolean,
    transitive: Boolean
  ): Dependency =
    Dependency(
      module,
      version,
      configuration,
      exclusions,
      Publication("", attributes.`type`, Extension(""), attributes.classifier),
      optional,
      transitive
    )
}
