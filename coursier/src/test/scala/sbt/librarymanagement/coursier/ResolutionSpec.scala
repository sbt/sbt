package sbt.librarymanagement.coursier

import sbt.librarymanagement.Configurations.Component
import sbt.librarymanagement.Resolver.{
  DefaultMavenRepository,
  JCenterRepository,
  JavaNet2Repository
}
import sbt.librarymanagement.syntax._
import sbt.librarymanagement.{ Resolver, UnresolvedWarningConfiguration, UpdateConfiguration }

class ResolutionSpec extends BaseCoursierSpecification {
  override val resolvers = Vector(
    DefaultMavenRepository,
    JavaNet2Repository,
    JCenterRepository,
    Resolver.sbtPluginRepo("releases")
  )

  val lmEngine = new CoursierDependencyResolution(resolvers)

  private final val stubModule = "com.example" % "foo" % "0.1.0" % "compile"

  "Coursier dependency resolution" should "resolve very simple module" in {
    val dependencies = Vector(
      "com.typesafe.scala-logging" % "scala-logging_2.12" % "3.7.2" % "compile",
      "org.scalatest" % "scalatest_2.12" % "3.0.4" % "test"
    ).map(_.withIsTransitive(false))

    val coursierModule = module(stubModule, dependencies, Some("2.12.4"))
    val resolution =
      lmEngine.update(coursierModule, UpdateConfiguration(), UnresolvedWarningConfiguration(), log)

    resolution should be('right)
    val r = resolution.right.get
    r.configurations.map(_.configuration) should have size 11

    val compileConfig = r.configurations.find(_.configuration == Compile.toConfigRef).get
    compileConfig.modules should have size 1

    val runtimeConfig = r.configurations.find(_.configuration == Runtime.toConfigRef).get
    runtimeConfig.modules should have size 1

    val testConfig = r.configurations.find(_.configuration == Test.toConfigRef).get
    testConfig.modules should have size 1
  }

  it should "resolve compiler bridge" in {
    val dependencies =
      Vector(("org.scala-sbt" % "compiler-interface" % "1.0.4" % "component").sources())
    val coursierModule = module(stubModule, dependencies, Some("2.12.4"))
    val resolution =
      lmEngine.update(coursierModule, UpdateConfiguration(), UnresolvedWarningConfiguration(), log)

    val r = resolution.right.get

    val componentConfig = r.configurations.find(_.configuration == Component.toConfigRef).get
    componentConfig.modules should have size 1
    componentConfig.modules.head.artifacts should have size 1
    componentConfig.modules.head.artifacts.head._1.classifier should contain("sources")
  }

  it should "resolve sbt jars" in {
    val dependencies =
      Vector(("org.scala-sbt" % "sbt" % "1.1.0" % "provided"))
    val coursierModule = module(stubModule, dependencies, Some("2.12.4"))
    val resolution =
      lmEngine.update(coursierModule, UpdateConfiguration(), UnresolvedWarningConfiguration(), log)

    val r = resolution.right.get

    val modules = r.configurations.flatMap(_.modules)
    modules.map(_.module.name) should contain("main_2.12")
  }

  it should "resolve with default resolvers" in {
    val dependencies =
      Vector(("org.scala-sbt" % "compiler-interface" % "1.0.4" % "component").sources())
    val lmEngine =
      CoursierDependencyResolution.apply(Resolver.combineDefaultResolvers(Vector.empty))
    val coursierModule = module(stubModule, dependencies, Some("2.12.4"))
    val resolution =
      lmEngine.update(coursierModule, UpdateConfiguration(), UnresolvedWarningConfiguration(), log)

    resolution should be('right)
  }

  it should "resolve plugin" in {
    val pluginAttributes = Map("scalaVersion" -> "2.12", "sbtVersion" -> "1.0")
    val dependencies =
      Vector(("org.xerial.sbt" % "sbt-sonatype" % "2.0").withExtraAttributes(pluginAttributes))
    val coursierModule = module(stubModule, dependencies, Some("2.12.4"))
    val resolution =
      lmEngine.update(coursierModule, UpdateConfiguration(), UnresolvedWarningConfiguration(), log)

    val r = resolution.right.get

    val componentConfig = r.configurations.find(_.configuration == Compile.toConfigRef).get
    componentConfig.modules.map(_.module.name) should have size 5
  }

  it should "strip e: prefix from plugin attributes" in {
    val pluginAttributes = Map("e:scalaVersion" -> "2.12", "e:sbtVersion" -> "1.0")
    val dependencies =
      Vector(("org.xerial.sbt" % "sbt-sonatype" % "2.0").withExtraAttributes(pluginAttributes))
    val coursierModule = module(stubModule, dependencies, Some("2.12.4"))
    val resolution =
      lmEngine.update(coursierModule, UpdateConfiguration(), UnresolvedWarningConfiguration(), log)

    resolution should be('right)
  }

  it should "resolve plugins hosted on repo.typesafe.com" in {
    val pluginAttributes = Map("e:scalaVersion" -> "2.12", "e:sbtVersion" -> "1.0")
    val dependencies =
      Vector(("com.typesafe.sbt" % "sbt-git" % "0.9.3").withExtraAttributes(pluginAttributes))
    val coursierModule = module(stubModule, dependencies, Some("2.12.4"))
    val resolution =
      lmEngine.update(coursierModule, UpdateConfiguration(), UnresolvedWarningConfiguration(), log)

    resolution should be('right)
  }

  it should "reorder fast and slow resolvers" in {
    val resolvers = Vector(
      JavaNet2Repository,
      Resolver.sbtPluginRepo("releases"),
      DefaultMavenRepository
    )
    val engine = new CoursierDependencyResolution(resolvers)
    engine.reorderedResolvers.head.name should be("public")
    engine.reorderedResolvers.last.name should be("sbt-plugin-releases")
    engine.reorderedResolvers should have size 3
  }

  it should "reorder default resolvers" in {
    val resolvers = Resolver.combineDefaultResolvers(Vector.empty)
    val engine = new CoursierDependencyResolution(resolvers)
    engine.reorderedResolvers should not be 'empty
    engine.reorderedResolvers.head.name should be("public")
  }
}
