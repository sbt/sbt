package lmcoursier.credentials

import dataclass.data

@data class FileCredentials(
    path: String,
    optional: Boolean = true // scruff: ignore(no_default_param)
) extends Credentials
