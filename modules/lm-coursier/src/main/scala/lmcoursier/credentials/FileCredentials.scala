package lmcoursier.credentials

import dataclass.data

@data class FileCredentials(
  path: String,
  optional: Boolean = true
) extends Credentials
