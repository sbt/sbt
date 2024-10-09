package lmcoursier.definitions

import dataclass.data

@data class DateTime(
  year: Int,
  month: Int,
  day: Int,
  hour: Int,
  minute: Int,
  second: Int
)
