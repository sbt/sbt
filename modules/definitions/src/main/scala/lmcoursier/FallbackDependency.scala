package lmcoursier

import java.net.URL

import dataclass.data
import lmcoursier.definitions.Module
//FIXME use URI instead of URL
@data class FallbackDependency(
  module: Module,
  version: String,
  url: URL,
  changing: Boolean
)
