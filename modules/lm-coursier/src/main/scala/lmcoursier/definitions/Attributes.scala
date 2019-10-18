package lmcoursier.definitions

import dataclass.data

@data class Attributes(
  `type`: Type,
  classifier: Classifier
)
