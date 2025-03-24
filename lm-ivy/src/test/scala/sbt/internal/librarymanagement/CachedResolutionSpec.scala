package sbt.internal.librarymanagement

import sbt.librarymanagement.*

class CachedResolutionSpec extends ResolutionSpec with BaseCachedResolutionSpec {
  override val resolvers = Vector(
    Resolver.mavenCentral,
    Resolver.sbtPluginRepo("releases")
  )
}
