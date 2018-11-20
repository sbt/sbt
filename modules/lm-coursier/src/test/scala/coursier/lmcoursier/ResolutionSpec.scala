package coursier.lmcoursier

import org.scalatest.{Matchers, PropSpec}
import sbt.internal.librarymanagement.cross.CrossVersionUtil
import sbt.internal.util.ConsoleLogger
import sbt.librarymanagement._
import sbt.librarymanagement.Configurations.Component
import sbt.librarymanagement.Resolver.{DefaultMavenRepository, JCenterRepository, JavaNet2Repository}
import sbt.librarymanagement.{Resolver, UnresolvedWarningConfiguration, UpdateConfiguration}
import sbt.librarymanagement.syntax._

final class ResolutionSpec extends PropSpec with Matchers {

  lazy val log = ConsoleLogger()

  def configurations = Vector(Compile, Test, Runtime, Provided, Optional, Component)
  def module(
    lmEngine: DependencyResolution,
    moduleId: ModuleID,
    deps: Vector[ModuleID],
    scalaFullVersion: Option[String],
    overrideScalaVersion: Boolean = true
  ): ModuleDescriptor = {
    val scalaModuleInfo = scalaFullVersion map { fv =>
      ScalaModuleInfo(
        scalaFullVersion = fv,
        scalaBinaryVersion = CrossVersionUtil.binaryScalaVersion(fv),
        configurations = configurations,
        checkExplicit = true,
        filterImplicit = false,
        overrideScalaVersion = overrideScalaVersion
      )
    }

    val moduleSetting = ModuleDescriptorConfiguration(moduleId, ModuleInfo("foo"))
      .withDependencies(deps)
      .withConfigurations(configurations)
      .withScalaModuleInfo(scalaModuleInfo)
    lmEngine.moduleDescriptor(moduleSetting)
  }

  def resolvers = Vector(
    DefaultMavenRepository,
    JavaNet2Repository,
    JCenterRepository,
    Resolver.sbtPluginRepo("releases")
  )

  val lmEngine = CoursierDependencyResolution(CoursierConfiguration().withResolvers(resolvers))

  private final val stubModule = "com.example" % "foo" % "0.1.0" % "compile"

  property("very simple module") {
    val dependencies = Vector(
      "com.typesafe.scala-logging" % "scala-logging_2.12" % "3.7.2" % "compile",
      "org.scalatest" % "scalatest_2.12" % "3.0.4" % "test"
    ).map(_.withIsTransitive(false))

    val coursierModule = module(lmEngine, stubModule, dependencies, Some("2.12.4"))
    val resolution =
      lmEngine.update(coursierModule, UpdateConfiguration(), UnresolvedWarningConfiguration(), log)

    resolution should be('right)
    val r = resolution.right.get
    r.configurations.map(_.configuration) should have size configurations.length

    val compileConfig = r.configurations.find(_.configuration == Compile.toConfigRef).get
    compileConfig.modules should have size 1

    val runtimeConfig = r.configurations.find(_.configuration == Runtime.toConfigRef).get
    runtimeConfig.modules should have size 1

    val testConfig = r.configurations.find(_.configuration == Test.toConfigRef).get
    testConfig.modules should have size 2
  }

  property("resolve compiler bridge") {
    val dependencies =
      Vector(("org.scala-sbt" % "compiler-interface" % "1.0.4" % "component").sources())
    val coursierModule = module(lmEngine, stubModule, dependencies, Some("2.12.4"))
    val resolution =
      lmEngine.update(coursierModule, UpdateConfiguration(), UnresolvedWarningConfiguration(), log)

    val r = resolution.right.get

    val componentConfig = r.configurations.find(_.configuration == Component.toConfigRef).get
    componentConfig.modules should have size 2
    componentConfig.modules.head.artifacts should have size 1
    componentConfig.modules.head.artifacts.head._1.classifier should contain("sources")
  }

  property("resolve sbt jars") {
    val dependencies =
      Vector("org.scala-sbt" % "sbt" % "1.1.0" % "provided")
    val coursierModule = module(lmEngine, stubModule, dependencies, Some("2.12.4"))
    val resolution =
      lmEngine.update(coursierModule, UpdateConfiguration(), UnresolvedWarningConfiguration(), log)

    val r = resolution.right.get

    val modules = r.configurations.flatMap(_.modules)
    modules.map(_.module.name) should contain("main_2.12")
  }

  property("resolve with default resolvers") {
    val dependencies =
      Vector(("org.scala-sbt" % "compiler-interface" % "1.0.4" % "component").sources())
    val lmEngine =
      CoursierDependencyResolution(
        CoursierConfiguration()
          .withResolvers(Resolver.combineDefaultResolvers(Vector.empty))
      )
    val coursierModule = module(lmEngine, stubModule, dependencies, Some("2.12.4"))
    val resolution =
      lmEngine.update(coursierModule, UpdateConfiguration(), UnresolvedWarningConfiguration(), log)

    resolution should be('right)
  }

  property("resolve plugin") {
    val pluginAttributes = Map("scalaVersion" -> "2.12", "sbtVersion" -> "1.0")
    val dependencies =
      Vector(("org.xerial.sbt" % "sbt-sonatype" % "2.0").withExtraAttributes(pluginAttributes))
    val coursierModule = module(lmEngine, stubModule, dependencies, Some("2.12.4"))
    val resolution =
      lmEngine.update(coursierModule, UpdateConfiguration(), UnresolvedWarningConfiguration(), log)

    val r = resolution.right.get

    val componentConfig = r.configurations.find(_.configuration == Compile.toConfigRef).get
    componentConfig.modules.map(_.module.name) should have size 5
  }

  property("strip e: prefix from plugin attributes") {
    val pluginAttributes = Map("e:scalaVersion" -> "2.12", "e:sbtVersion" -> "1.0")
    val dependencies =
      Vector(("org.xerial.sbt" % "sbt-sonatype" % "2.0").withExtraAttributes(pluginAttributes))
    val coursierModule = module(lmEngine, stubModule, dependencies, Some("2.12.4"))
    val resolution =
      lmEngine.update(coursierModule, UpdateConfiguration(), UnresolvedWarningConfiguration(), log)

    resolution should be('right)
  }

  property("resolve plugins hosted on repo.typesafe.com") {
    val pluginAttributes = Map("e:scalaVersion" -> "2.12", "e:sbtVersion" -> "1.0")
    val dependencies =
      Vector(("com.typesafe.sbt" % "sbt-git" % "0.9.3").withExtraAttributes(pluginAttributes))
    val coursierModule = module(lmEngine, stubModule, dependencies, Some("2.12.4"))
    val resolution =
      lmEngine.update(coursierModule, UpdateConfiguration(), UnresolvedWarningConfiguration(), log)

    resolution should be('right)
  }

  property("reorder fast and slow resolvers") {
    val resolvers = Vector(
      JavaNet2Repository,
      Resolver.sbtPluginRepo("releases"),
      DefaultMavenRepository
    )
    val engine = new CoursierDependencyResolution(CoursierConfiguration().withResolvers(resolvers))
    engine.resolvers.last.name should be("sbt-plugin-releases")
    engine.resolvers should have size resolvers.length
  }
}
