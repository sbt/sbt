package sbt.internal.librarymanagement

import sbt.librarymanagement._

class IvyResolutionSpec extends ResolutionSpec with BaseIvySpecification {
  override val resolvers = Vector(
    Resolver.mavenCentral,
    Resolver.sbtPluginRepo("releases")
  )
}
