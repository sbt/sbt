package sbt.internal.librarymanagement

import sbt.librarymanagement.*

class IvyResolutionSpec extends ResolutionSpec with BaseIvySpecification {
  override val resolvers = Vector(
    Resolver.mavenCentral,
    Resolver.sbtPluginRepo("releases")
  )
}
