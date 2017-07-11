package sbt.librarymanagement

import sbt.librarymanagement.ivy.UpdateOptions
import sbt.internal.librarymanagement.BaseIvySpecification
import sbt.librarymanagement.syntax._
import Resolver._

class ModuleResolversTest extends BaseIvySpecification {
  override final val resolvers = Vector(
    DefaultMavenRepository,
    JavaNet2Repository,
    JCenterRepository,
    Resolver.sbtPluginRepo("releases")
  )

  private final val stubModule = "com.example" % "foo" % "0.1.0" % "compile"
  val pluginAttributes = Map("sbtVersion" -> "0.13", "scalaVersion" -> "2.10")
  private final val dependencies = Vector(
    ("me.lessis" % "bintray-sbt" % "0.3.0" % "compile").withExtraAttributes(pluginAttributes),
    "com.jfrog.bintray.client" % "bintray-client-java-api" % "0.9.2" % "compile"
  ).map(_.withIsTransitive(false))

  "The direct resolvers in update options" should "skip the rest of resolvers" in {
    cleanIvyCache()
    val updateOptions = UpdateOptions()
    val ivyModule = module(stubModule, dependencies, None, updateOptions)
    val normalResolution = ivyUpdateEither(ivyModule)
    assert(normalResolution.isRight)
    val normalResolutionTime = normalResolution.right.get.stats.resolveTime

    cleanIvyCache()
    val moduleResolvers = Map(
      dependencies.head -> resolvers.last,
      dependencies.tail.head -> resolvers.init.last
    )
    val customUpdateOptions = updateOptions.withModuleResolvers(moduleResolvers)
    val ivyModule2 = module(stubModule, dependencies, None, customUpdateOptions)
    val fasterResolution = ivyUpdateEither(ivyModule2)
    assert(fasterResolution.isRight)
    val fasterResolutionTime = fasterResolution.right.get.stats.resolveTime

    // THis is left on purpose so that in spurious error we see the times
    println(s"NORMAL RESOLUTION TIME $normalResolutionTime")
    println(s"FASTER RESOLUTION TIME $fasterResolutionTime")

    // Check that faster resolution is at least 1/5 faster than normal resolution
    // This is a conservative check just to make sure we don't regress -- speedup is higher
    assert(fasterResolutionTime <= (normalResolutionTime * 0.80))
  }
}
