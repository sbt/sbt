package coursier.lmcoursier

import java.net.URL

import coursier.core.Module

// FIXME Handle that via the contraband thing?
final case class FallbackDependency(
  module: Module,
  version: String,
  url: URL,
  changing: Boolean
)
