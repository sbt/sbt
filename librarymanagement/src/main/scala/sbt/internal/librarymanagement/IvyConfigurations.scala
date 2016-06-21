/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt.internal.librarymanagement

import java.io.File
import java.net.URI
import scala.xml.NodeSeq
import sbt.util.Logger
import sbt.librarymanagement._

final class IvyPaths(val baseDirectory: File, val ivyHome: Option[File]) {
  def withBase(newBaseDirectory: File) = new IvyPaths(newBaseDirectory, ivyHome)
  override def toString = s"IvyPaths($baseDirectory, $ivyHome)"
}
sealed trait IvyConfiguration {
  type This <: IvyConfiguration
  def lock: Option[xsbti.GlobalLock]
  def baseDirectory: File
  def log: Logger
  def withBase(newBaseDirectory: File): This
  def updateOptions: UpdateOptions
}
final class InlineIvyConfiguration(val paths: IvyPaths, val resolvers: Seq[Resolver], val otherResolvers: Seq[Resolver],
  val moduleConfigurations: Seq[ModuleConfiguration], val localOnly: Boolean, val lock: Option[xsbti.GlobalLock],
  val checksums: Seq[String], val resolutionCacheDir: Option[File], val updateOptions: UpdateOptions,
  val log: Logger) extends IvyConfiguration {
  @deprecated("Use the variant that accepts resolutionCacheDir and updateOptions.", "0.13.0")
  def this(paths: IvyPaths, resolvers: Seq[Resolver], otherResolvers: Seq[Resolver],
    moduleConfigurations: Seq[ModuleConfiguration], localOnly: Boolean, lock: Option[xsbti.GlobalLock],
    checksums: Seq[String], log: Logger) =
    this(paths, resolvers, otherResolvers, moduleConfigurations, localOnly, lock, checksums, None, UpdateOptions(), log)

  @deprecated("Use the variant that accepts updateOptions.", "0.13.6")
  def this(paths: IvyPaths, resolvers: Seq[Resolver], otherResolvers: Seq[Resolver],
    moduleConfigurations: Seq[ModuleConfiguration], localOnly: Boolean, lock: Option[xsbti.GlobalLock],
    checksums: Seq[String], resolutionCacheDir: Option[File], log: Logger) =
    this(paths, resolvers, otherResolvers, moduleConfigurations, localOnly, lock, checksums, resolutionCacheDir, UpdateOptions(), log)

  override def toString: String = s"InlineIvyConfiguration($paths, $resolvers, $otherResolvers, " +
    s"$moduleConfigurations, $localOnly, $checksums, $resolutionCacheDir, $updateOptions)"

  type This = InlineIvyConfiguration
  def baseDirectory = paths.baseDirectory
  def withBase(newBase: File) = new InlineIvyConfiguration(paths.withBase(newBase), resolvers, otherResolvers, moduleConfigurations, localOnly, lock, checksums,
    resolutionCacheDir, updateOptions, log)
  def changeResolvers(newResolvers: Seq[Resolver]) = new InlineIvyConfiguration(paths, newResolvers, otherResolvers, moduleConfigurations, localOnly, lock, checksums,
    resolutionCacheDir, updateOptions, log)

  override def equals(o: Any): Boolean = o match {
    case o: InlineIvyConfiguration =>
      this.paths == o.paths &&
        this.resolvers == o.resolvers &&
        this.otherResolvers == o.otherResolvers &&
        this.moduleConfigurations == o.moduleConfigurations &&
        this.localOnly == o.localOnly &&
        this.checksums == o.checksums &&
        this.resolutionCacheDir == o.resolutionCacheDir &&
        this.updateOptions == o.updateOptions
    case _ => false
  }

  override def hashCode: Int =
    {
      var hash = 1
      hash = hash * 31 + this.paths.##
      hash = hash * 31 + this.resolvers.##
      hash = hash * 31 + this.otherResolvers.##
      hash = hash * 31 + this.moduleConfigurations.##
      hash = hash * 31 + this.localOnly.##
      hash = hash * 31 + this.checksums.##
      hash = hash * 31 + this.resolutionCacheDir.##
      hash = hash * 31 + this.updateOptions.##
      hash
    }
}
final class ExternalIvyConfiguration(val baseDirectory: File, val uri: URI, val lock: Option[xsbti.GlobalLock],
  val extraResolvers: Seq[Resolver], val updateOptions: UpdateOptions, val log: Logger) extends IvyConfiguration {
  @deprecated("Use the variant that accepts updateOptions.", "0.13.6")
  def this(baseDirectory: File, uri: URI, lock: Option[xsbti.GlobalLock], extraResolvers: Seq[Resolver], log: Logger) =
    this(baseDirectory, uri, lock, extraResolvers, UpdateOptions(), log)

  type This = ExternalIvyConfiguration
  def withBase(newBase: File) = new ExternalIvyConfiguration(newBase, uri, lock, extraResolvers, UpdateOptions(), log)
}
object ExternalIvyConfiguration {
  def apply(baseDirectory: File, file: File, lock: Option[xsbti.GlobalLock], log: Logger) = new ExternalIvyConfiguration(baseDirectory, file.toURI, lock, Nil, UpdateOptions(), log)
}

object IvyConfiguration {
  /**
   * Called to configure Ivy when inline resolvers are not specified.
   * This will configure Ivy with an 'ivy-settings.xml' file if there is one or else use default resolvers.
   */
  @deprecated("Explicitly use either external or inline configuration.", "0.12.0")
  def apply(paths: IvyPaths, lock: Option[xsbti.GlobalLock], localOnly: Boolean, checksums: Seq[String], log: Logger): IvyConfiguration =
    {
      log.debug("Autodetecting configuration.")
      val defaultIvyConfigFile = IvySbt.defaultIvyConfiguration(paths.baseDirectory)
      if (defaultIvyConfigFile.canRead)
        ExternalIvyConfiguration(paths.baseDirectory, defaultIvyConfigFile, lock, log)
      else
        new InlineIvyConfiguration(paths, Resolver.withDefaultResolvers(Nil), Nil, Nil, localOnly, lock, checksums, None, log)
    }
}

sealed trait ModuleSettings {
  def validate: Boolean
  def ivyScala: Option[IvyScala]
  def noScala: ModuleSettings
}
final case class IvyFileConfiguration(file: File, ivyScala: Option[IvyScala], validate: Boolean, autoScalaTools: Boolean = true) extends ModuleSettings {
  def noScala = copy(ivyScala = None)
}
final case class PomConfiguration(file: File, ivyScala: Option[IvyScala], validate: Boolean, autoScalaTools: Boolean = true) extends ModuleSettings {
  def noScala = copy(ivyScala = None)
}

final class InlineConfiguration private[sbt] (
  val module: ModuleID,
  val moduleInfo: ModuleInfo,
  val dependencies: Seq[ModuleID],
  val overrides: Set[ModuleID],
  val excludes: Seq[SbtExclusionRule],
  val ivyXML: NodeSeq,
  val configurations: Seq[Configuration],
  val defaultConfiguration: Option[Configuration],
  val ivyScala: Option[IvyScala],
  val validate: Boolean,
  val conflictManager: ConflictManager
) extends ModuleSettings {
  def withConfigurations(configurations: Seq[Configuration]) = copy(configurations = configurations)
  def noScala = copy(ivyScala = None)
  def withOverrides(overrides: Set[ModuleID]): ModuleSettings =
    copy(overrides = overrides)

  private[sbt] def copy(
    module: ModuleID = this.module,
    moduleInfo: ModuleInfo = this.moduleInfo,
    dependencies: Seq[ModuleID] = this.dependencies,
    overrides: Set[ModuleID] = this.overrides,
    excludes: Seq[SbtExclusionRule] = this.excludes,
    ivyXML: NodeSeq = this.ivyXML,
    configurations: Seq[Configuration] = this.configurations,
    defaultConfiguration: Option[Configuration] = this.defaultConfiguration,
    ivyScala: Option[IvyScala] = this.ivyScala,
    validate: Boolean = this.validate,
    conflictManager: ConflictManager = this.conflictManager
  ): InlineConfiguration =
    InlineConfiguration(module, moduleInfo, dependencies, overrides, excludes, ivyXML,
      configurations, defaultConfiguration, ivyScala, validate, conflictManager)

  override def toString: String =
    s"InlineConfiguration($module, $moduleInfo, $dependencies, $overrides, $excludes, " +
      s"$ivyXML, $configurations, $defaultConfiguration, $ivyScala, $validate, $conflictManager)"

  override def equals(o: Any): Boolean = o match {
    case o: InlineConfiguration =>
      this.module == o.module &&
        this.moduleInfo == o.moduleInfo &&
        this.dependencies == o.dependencies &&
        this.overrides == o.overrides &&
        this.excludes == o.excludes &&
        this.ivyXML == o.ivyXML &&
        this.configurations == o.configurations &&
        this.defaultConfiguration == o.defaultConfiguration &&
        this.ivyScala == o.ivyScala &&
        this.validate == o.validate &&
        this.conflictManager == o.conflictManager
    case _ => false
  }

  override def hashCode: Int =
    {
      var hash = 1
      hash = hash * 31 + this.module.##
      hash = hash * 31 + this.dependencies.##
      hash = hash * 31 + this.overrides.##
      hash = hash * 31 + this.excludes.##
      hash = hash * 31 + this.ivyXML.##
      hash = hash * 31 + this.configurations.##
      hash = hash * 31 + this.defaultConfiguration.##
      hash = hash * 31 + this.ivyScala.##
      hash = hash * 31 + this.validate.##
      hash = hash * 31 + this.conflictManager.##
      hash
    }
}
object InlineConfiguration {
  def apply(
    module: ModuleID,
    moduleInfo: ModuleInfo,
    dependencies: Seq[ModuleID],
    overrides: Set[ModuleID] = Set.empty,
    excludes: Seq[SbtExclusionRule] = Nil,
    ivyXML: NodeSeq = NodeSeq.Empty,
    configurations: Seq[Configuration] = Nil,
    defaultConfiguration: Option[Configuration] = None,
    ivyScala: Option[IvyScala] = None,
    validate: Boolean = false,
    conflictManager: ConflictManager = ConflictManager.default
  ): InlineConfiguration =
    new InlineConfiguration(module, moduleInfo, dependencies, overrides, excludes, ivyXML,
      configurations, defaultConfiguration, ivyScala, validate, conflictManager)

  def configurations(explicitConfigurations: Iterable[Configuration], defaultConfiguration: Option[Configuration]) =
    if (explicitConfigurations.isEmpty) {
      defaultConfiguration match {
        case Some(Configurations.DefaultIvyConfiguration) => Configurations.Default :: Nil
        case Some(Configurations.DefaultMavenConfiguration) => Configurations.defaultMavenConfigurations
        case _ => Nil
      }
    } else
      explicitConfigurations
}
