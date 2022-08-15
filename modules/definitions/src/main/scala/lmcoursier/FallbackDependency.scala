package lmcoursier

import java.net.URL

import dataclass.data
import lmcoursier.definitions.Module

@data class FallbackDependency(
  module: Module,
  version: String,
  url: URL,
  changing: Boolean
)
