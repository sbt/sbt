package lmcoursier.definitions

import dataclass.data

@data class Publication(
  name: String,
  `type`: Type,
  ext: Extension,
  classifier: Classifier
)
