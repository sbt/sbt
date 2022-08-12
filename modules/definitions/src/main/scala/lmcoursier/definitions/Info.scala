package lmcoursier.definitions

import dataclass.data

@data class Info(
  description: String,
  homePage: String,
  licenses: Seq[(String, Option[String])],
  developers: Seq[Developer],
  publication: Option[DateTime]
)
