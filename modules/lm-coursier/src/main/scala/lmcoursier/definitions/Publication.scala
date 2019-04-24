package lmcoursier.definitions

final case class Publication(
  name: String,
  `type`: Type,
  ext: Extension,
  classifier: Classifier
)
