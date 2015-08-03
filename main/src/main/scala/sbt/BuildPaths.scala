/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

import java.io.File
import java.net.URI
import KeyRanks.DSetting

object BuildPaths {
  val globalBaseDirectory = AttributeKey[File]("global-base-directory", "The base directory for global sbt configuration and staging.", DSetting)
  val globalPluginsDirectory = AttributeKey[File]("global-plugins-directory", "The base directory for global sbt plugins.", DSetting)
  val globalSettingsDirectory = AttributeKey[File]("global-settings-directory", "The base directory for global sbt settings.", DSetting)
  val stagingDirectory = AttributeKey[File]("staging-directory", "The directory for staging remote projects.", DSetting)
  val dependencyBaseDirectory = AttributeKey[File]("dependency-base-directory", "The base directory for caching dependency resolution.", DSetting)

  import Path._

  def getGlobalBase(state: State): File = {
    val default = defaultVersionedGlobalBase(binarySbtVersion(state))
    def getDefault = { checkTransition(state, default); default }
    getFileSetting(globalBaseDirectory, GlobalBaseProperty, getDefault)(state)
  }
  private[this] def checkTransition(state: State, versioned: File): Unit = {
    val unversioned = defaultGlobalBase
    def globalDefined(base: File): Boolean =
      getGlobalPluginsDirectory(state, base).exists ||
        configurationSources(getGlobalSettingsDirectory(state, base)).exists(_.exists)
    val warnTransition = !globalDefined(versioned) && globalDefined(unversioned)
    if (warnTransition)
      state.log.warn(globalDirTransitionWarning(unversioned, versioned))
  }

  def getStagingDirectory(state: State, globalBase: File): File =
    fileSetting(stagingDirectory, StagingProperty, defaultStaging(globalBase))(state)

  def getGlobalPluginsDirectory(state: State, globalBase: File): File =
    fileSetting(globalPluginsDirectory, GlobalPluginsProperty, defaultGlobalPlugins(globalBase))(state)

  def getGlobalSettingsDirectory(state: State, globalBase: File): File =
    fileSetting(globalSettingsDirectory, GlobalSettingsProperty, globalBase)(state)

  def getDependencyDirectory(state: State, globalBase: File): File =
    fileSetting(dependencyBaseDirectory, DependencyBaseProperty, defaultDependencyBase(globalBase))(state)

  private[this] def fileSetting(stateKey: AttributeKey[File], property: String, default: File)(state: State): File =
    getFileSetting(stateKey, property, default)(state)

  def getFileSetting(stateKey: AttributeKey[File], property: String, default: => File)(state: State): File =
    state get stateKey orElse getFileProperty(property) getOrElse default

  def getFileProperty(name: String): Option[File] = Option(System.getProperty(name)) flatMap { path =>
    if (path.isEmpty) None else Some(new File(path))
  }

  def defaultVersionedGlobalBase(sbtVersion: String): File = defaultGlobalBase / sbtVersion
  def defaultGlobalBase = Path.userHome / ConfigDirectoryName

  private[this] def binarySbtVersion(state: State): String =
    sbt.cross.CrossVersionUtil.binarySbtVersion(state.configuration.provider.id.version)
  private[this] def defaultStaging(globalBase: File) = globalBase / "staging"
  private[this] def defaultGlobalPlugins(globalBase: File) = globalBase / PluginsDirectoryName
  private[this] def defaultDependencyBase(globalBase: File) = globalBase / "dependency"

  def configurationSources(base: File): Seq[File] = (base * (GlobFilter("*.sbt") - ".sbt")).get
  def pluginDirectory(definitionBase: File) = definitionBase / PluginsDirectoryName

  def evalOutputDirectory(base: File) = outputDirectory(base) / "config-classes"
  def outputDirectory(base: File) = base / DefaultTargetName

  def projectStandard(base: File) = base / "project"

  @deprecated("Use projectStandard.  The alternative project directory location has been removed.", "0.13.0")
  def projectHidden(base: File) = projectStandard(base)
  @deprecated("Use projectStandard.  The alternative project directory location has been removed.", "0.13.0")
  def selectProjectDir(base: File, log: Logger) = projectStandard(base)

  final val PluginsDirectoryName = "plugins"
  final val DefaultTargetName = "target"
  final val ConfigDirectoryName = ".sbt"
  final val GlobalBaseProperty = "sbt.global.base"
  final val StagingProperty = "sbt.global.staging"
  final val GlobalPluginsProperty = "sbt.global.plugins"
  final val GlobalSettingsProperty = "sbt.global.settings"
  final val DependencyBaseProperty = "sbt.dependency.base"

  def crossPath(base: File, instance: xsbti.compile.ScalaInstance): File = base / ("scala_" + instance.version)

  private[this] def globalDirTransitionWarning(unversioned: File, versioned: File): String =
    s"""The global sbt directory is now versioned and is located at $versioned.
  You are seeing this warning because there is global configuration in $unversioned but not in $versioned.
  The global sbt directory may be changed via the $GlobalBaseProperty system property.
"""
}
