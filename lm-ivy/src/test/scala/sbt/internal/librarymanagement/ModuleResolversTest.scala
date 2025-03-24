package sbt.internal.librarymanagement

import sbt.librarymanagement.*
import sbt.librarymanagement.syntax.*
import sbt.librarymanagement.ivy.UpdateOptions

object ModuleResolversTest extends BaseIvySpecification {
  override final val resolvers = Vector(
    MavenRepository(
      "JFrog OSS Releases",
      "https://releases.jfrog.io/artifactory/oss-releases/"
    ),
    Resolver.sbtPluginRepo("releases")
  )

  private final val stubModule = "com.example" % "foo" % "0.1.0" % "compile"
  val pluginAttributes = Map("sbtVersion" -> "0.13", "scalaVersion" -> "2.10")
  private final val dependencies = Vector(
    ("me.lessis" % "bintray-sbt" % "0.3.0" % "compile").withExtraAttributes(pluginAttributes),
    "com.jfrog.bintray.client" % "bintray-client-java-api" % "0.9.2" % "compile"
  ).map(_.withIsTransitive(false))

  test("The direct resolvers in update options should skip the rest of resolvers") {
    cleanIvyCache()
    val updateOptions = UpdateOptions()
    val ivyModule = module(stubModule, dependencies, None, updateOptions)
    val normalResolution = ivyUpdateEither(ivyModule)
    assert(normalResolution.isRight)
    val normalResolutionTime =
      normalResolution.fold(e => throw e.resolveException, identity).stats.resolveTime

    cleanIvyCache()
    val moduleResolvers = Map(
      dependencies.head -> resolvers.last,
      dependencies.tail.head -> resolvers.init.last
    )
    val customUpdateOptions = updateOptions.withModuleResolvers(moduleResolvers)
    val ivyModule2 = module(stubModule, dependencies, None, customUpdateOptions)
    val fasterResolution = ivyUpdateEither(ivyModule2)
    assert(fasterResolution.isRight)
    val fasterResolutionTime =
      fasterResolution.fold(e => throw e.resolveException, identity).stats.resolveTime

    // THis is left on purpose so that in spurious error we see the times
    println(s"NORMAL RESOLUTION TIME $normalResolutionTime")
    println(s"FASTER RESOLUTION TIME $fasterResolutionTime")

    // Check that faster resolution is faster
    assert(fasterResolutionTime < normalResolutionTime)
  }
}
