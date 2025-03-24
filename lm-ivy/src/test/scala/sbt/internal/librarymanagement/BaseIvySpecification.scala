package sbt.internal.librarymanagement

import sbt.io.IO
import sbt.io.syntax.*
import java.io.File
import sbt.internal.util.ConsoleLogger
import sbt.librarymanagement.*
import sbt.librarymanagement.ivy.*
import cross.CrossVersionUtil
import Configurations.*

trait BaseIvySpecification extends AbstractEngineSpec {
  def currentBase: File = new File(".")
  def label: String = this.getClass.getSimpleName
  def currentTarget: File = currentBase / "target" / label / "ivyhome"
  def currentManaged: File = currentBase / "target" / label / "lib_managed"
  def currentDependency: File = currentBase / "target" / label / "dependency"
  def defaultModuleId: ModuleID =
    ModuleID("com.example", "foo", "0.1.0").withConfigurations(Some("compile"))

  def scala2_13 = "2.13.10"

  lazy val log = ConsoleLogger()
  def lmEngine(uo: UpdateOptions = UpdateOptions()): DependencyResolution =
    IvyDependencyResolution(mkIvyConfiguration(uo))

  def configurations = Vector(Compile, Test, Runtime)

  def module(
      moduleId: ModuleID,
      deps: Vector[ModuleID],
      scalaFullVersion: Option[String]
  ): ModuleDescriptor = {
    module(moduleId, deps, scalaFullVersion, UpdateOptions(), true)
  }

  def module(
      moduleId: ModuleID,
      deps: Vector[ModuleID],
      scalaFullVersion: Option[String],
      uo: UpdateOptions = UpdateOptions(),
      overrideScalaVersion: Boolean = true,
      appendSbtCrossVersion: Boolean = false,
      platform: Option[String] = None,
  ): IvySbt#Module = {
    val scalaModuleInfo = scalaFullVersion map { fv =>
      ScalaModuleInfo(
        scalaFullVersion = fv,
        scalaBinaryVersion = CrossVersionUtil.binaryScalaVersion(fv),
        configurations = Vector.empty,
        checkExplicit = true,
        filterImplicit = false,
        overrideScalaVersion = overrideScalaVersion
      )
        .withPlatform(platform)
    }

    val moduleSetting: ModuleSettings = ModuleDescriptorConfiguration(moduleId, ModuleInfo("foo"))
      .withDependencies(deps)
      .withConfigurations(configurations)
      .withScalaModuleInfo(scalaModuleInfo)
    val ivySbt = new IvySbt(mkIvyConfiguration(uo))
    new ivySbt.Module(moduleSetting, appendSbtCrossVersion)
  }

  def resolvers: Vector[Resolver] = Vector(Resolver.mavenCentral)

  def chainResolver = ChainedResolver("sbt-chain", resolvers)

  def mkIvyConfiguration(uo: UpdateOptions): IvyConfiguration = {
    val moduleConfs = Vector(ModuleConfiguration("*", chainResolver))
    val resCacheDir = currentTarget / "resolution-cache"
    InlineIvyConfiguration()
      .withPaths(IvyPaths(currentBase.toString, Some(currentTarget.toString)))
      .withResolvers(resolvers)
      .withModuleConfigurations(moduleConfs)
      .withChecksums(Vector.empty)
      .withResolutionCacheDir(resCacheDir)
      .withLog(log)
      .withUpdateOptions(uo)
  }

  def makeUpdateConfiguration(
      offline: Boolean,
      metadataDirectory: Option[File]
  ): UpdateConfiguration = {
    val retrieveConfig = RetrieveConfiguration()
      .withRetrieveDirectory(currentManaged)
      .withOutputPattern(Resolver.defaultRetrievePattern)
      .withSync(false)

    UpdateConfiguration()
      .withRetrieveManaged(retrieveConfig)
      .withLogging(UpdateLogging.Full)
      .withOffline(offline)
      .withMetadataDirectory(metadataDirectory)
  }

  def updateEither(module: ModuleDescriptor): Either[UnresolvedWarning, UpdateReport] =
    ivyUpdateEither(module)

  def ivyUpdateEither(module: ModuleDescriptor): Either[UnresolvedWarning, UpdateReport] = {
    module match {
      case m: IvySbt#Module =>
        val config = makeUpdateConfiguration(false, Some(currentDependency))
        IvyActions.updateEither(m, config, UnresolvedWarningConfiguration(), log)
    }
  }

  def cleanCache(): Unit = cleanIvyCache()
  def cleanIvyCache(): Unit = IO.delete(currentTarget / "cache")

  override def cleanCachedResolutionCache(module: ModuleDescriptor): Unit = {
    module match {
      case m: IvySbt#Module => IvyActions.cleanCachedResolutionCache(m, log)
    }
  }

  def ivyUpdate(module: ModuleDescriptor): UpdateReport =
    update(module)

  def mkPublishConfiguration(
      resolver: Resolver,
      artifacts: Map[Artifact, File]
  ): PublishConfiguration = {
    PublishConfiguration()
      .withResolverName(resolver.name)
      .withArtifacts(artifacts.toVector)
      .withChecksums(Vector.empty)
      .withOverwrite(true)
  }

  def ivyPublish(module: IvySbt#Module, config: PublishConfiguration) = {
    IvyActions.publish(module, config, log)
  }
}
