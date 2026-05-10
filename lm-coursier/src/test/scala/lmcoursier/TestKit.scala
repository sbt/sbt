package lmcoursier

import sbt.internal.librarymanagement.cross.CrossVersionUtil
import sbt.internal.util.ConsoleLogger
import sbt.librarymanagement.*
import sbt.librarymanagement.Configurations.Component
import sbt.librarymanagement.Resolver.{ DefaultMavenRepository, JavaNet2Repository }
// import sbt.util.ShowLines.*
import sbt.librarymanagement.syntax.*

object TestKit:
  lazy val lmEngine = CoursierDependencyResolution(CoursierConfiguration().withResolvers(resolvers))
  lazy val log = ConsoleLogger()

  def configurations = Vector(Compile, Test, Runtime, Provided, Optional, Component)

  def resolvers = Vector(
    DefaultMavenRepository,
    JavaNet2Repository,
    Resolver.sbtPluginRepo("releases")
  )

  def coursierUpdate(m: ModuleDescriptor): UpdateReport =
    lmEngine.update(m, UpdateConfiguration(), UnresolvedWarningConfiguration(), log) match
      case Right(ur) => ur
      case Left(w)   => sys.error(w.toString)

  def module(
      moduleId: ModuleID,
      deps: Vector[ModuleID],
      scalaFullVersion: Option[String],
  ): ModuleDescriptor =
    module(
      lmEngine = lmEngine,
      moduleId = moduleId,
      deps = deps,
      scalaFullVersion = scalaFullVersion,
      overrideScalaVersion = true,
    )

  def module(
      moduleId: ModuleID,
      deps: Vector[ModuleID],
      scalaFullVersion: Option[String],
      overrideScalaVersion: Boolean,
  ): ModuleDescriptor =
    module(
      lmEngine = lmEngine,
      moduleId = moduleId,
      deps = deps,
      scalaFullVersion = scalaFullVersion,
      overrideScalaVersion = overrideScalaVersion,
    )

  def module(
      lmEngine: DependencyResolution,
      moduleId: ModuleID,
      deps: Vector[ModuleID],
      scalaFullVersion: Option[String],
      overrideScalaVersion: Boolean,
  ): ModuleDescriptor =
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

  def defaultModuleId: ModuleID =
    ModuleID("com.example", "foo", "0.1.0").withConfigurations(Some("compile"))

end TestKit
