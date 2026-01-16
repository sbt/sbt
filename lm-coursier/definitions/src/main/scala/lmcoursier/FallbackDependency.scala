package lmcoursier

import java.net.URI

import dataclass.data
import lmcoursier.definitions.Module

@data class FallbackDependency(
    module: Module,
    version: String,
    uri: URI,
    changing: Boolean
)
