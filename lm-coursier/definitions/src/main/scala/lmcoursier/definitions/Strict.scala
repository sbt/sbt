package lmcoursier.definitions

import dataclass.*

@data class Strict(
    include: Set[(String, String)] = Set(("*", "*")), // scruff: ignore(no_default_param)
    exclude: Set[(String, String)] = Set.empty, // scruff: ignore(no_default_param)
    ignoreIfForcedVersion: Boolean = true, // scruff: ignore(no_default_param)
    @since
    includeByDefault: Boolean = false, // scruff: ignore(no_default_param)
    semVer: Boolean = false // scruff: ignore(no_default_param)
)
