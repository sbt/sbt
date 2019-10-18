package lmcoursier.definitions

import dataclass.data

@data class Publication(
  name: String,
  `type`: Type,
  ext: Extension,
  classifier: Classifier
) {
  def attributes: Attributes =
    Attributes(`type`, classifier)
  def withAttributes(attributes: Attributes): Publication =
    withType(attributes.`type`)
      .withClassifier(attributes.classifier)
}
