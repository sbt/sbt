package lmcoursier.definitions

import dataclass.*

@data class Strict(
    include: Set[(String, String)] = Set(("*", "*")),
    exclude: Set[(String, String)] = Set.empty,
    ignoreIfForcedVersion: Boolean = true,
    @since
    includeByDefault: Boolean = false,
    semVer: Boolean = false
)
