/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import KeyRanks.DSetting

import sbt.io.{ GlobFilter, Path }
import sbt.internal.util.AttributeKey

object BuildPaths {
  val globalBaseDirectory = AttributeKey[File](
    "global-base-directory",
    "The base directory for global sbt configuration and staging.",
    DSetting
  )
  val globalPluginsDirectory = AttributeKey[File](
    "global-plugins-directory",
    "The base directory for global sbt plugins.",
    DSetting
  )
  val globalSettingsDirectory = AttributeKey[File](
    "global-settings-directory",
    "The base directory for global sbt settings.",
    DSetting
  )
  val stagingDirectory =
    AttributeKey[File]("staging-directory", "The directory for staging remote projects.", DSetting)
  val dependencyBaseDirectory = AttributeKey[File](
    "dependency-base-directory",
    "The base directory for caching dependency resolution.",
    DSetting
  )

  val globalZincDirectory =
    AttributeKey[File]("global-zinc-directory", "The base directory for Zinc internals.", DSetting)

  import sbt.io.syntax._

  def getGlobalBase(state: State): File = {
    val default = defaultVersionedGlobalBase(binarySbtVersion(state))
    getFileSetting(globalBaseDirectory, GlobalBaseProperty, default)(state)
  }

  def getStagingDirectory(state: State, globalBase: File): File =
    fileSetting(stagingDirectory, StagingProperty, defaultStaging(globalBase))(state)

  def getGlobalPluginsDirectory(state: State, globalBase: File): File =
    fileSetting(globalPluginsDirectory, GlobalPluginsProperty, defaultGlobalPlugins(globalBase))(
      state
    )

  def getGlobalSettingsDirectory(state: State, globalBase: File): File =
    fileSetting(globalSettingsDirectory, GlobalSettingsProperty, globalBase)(state)

  def getDependencyDirectory(state: State, globalBase: File): File =
    fileSetting(dependencyBaseDirectory, DependencyBaseProperty, defaultDependencyBase(globalBase))(
      state
    )

  def getZincDirectory(state: State, globalBase: File): File =
    fileSetting(globalZincDirectory, GlobalZincProperty, defaultGlobalZinc(globalBase))(state)

  private[this] def fileSetting(stateKey: AttributeKey[File], property: String, default: File)(
      state: State
  ): File =
    getFileSetting(stateKey, property, default)(state)

  def getFileSetting(stateKey: AttributeKey[File], property: String, default: => File)(
      state: State
  ): File =
    state get stateKey orElse getFileProperty(property) getOrElse default

  def getFileProperty(name: String): Option[File] = Option(System.getProperty(name)) flatMap {
    path =>
      if (path.isEmpty) None
      else {
        if (path.head == '~') {
          val tildePath = expandTildePrefix(path)
          Some(new File(tildePath))
        } else {
          Some(new File(path))
        }
      }
  }

  def expandTildePrefix(path: String): String = {
    val tildePath = path.split("\\/").headOption match {
      case Some("~")  => sys.env.getOrElse("HOME", "")
      case Some("~+") => sys.env.getOrElse("PWD", "")
      case Some("~-") => sys.env.getOrElse("OLDPWD", "")
      case _          => ""
    }

    path.indexOf("/") match {
      case -1 => tildePath
      case _  => tildePath + path.substring(path.indexOf("/"))
    }
  }

  def defaultVersionedGlobalBase(sbtVersion: String): File = defaultGlobalBase / sbtVersion
  def defaultGlobalBase = Path.userHome / ConfigDirectoryName

  private[this] def binarySbtVersion(state: State): String =
    sbt.internal.librarymanagement.cross.CrossVersionUtil
      .binarySbtVersion(state.configuration.provider.id.version)
  private[this] def defaultStaging(globalBase: File) = globalBase / "staging"
  private[this] def defaultGlobalPlugins(globalBase: File) = globalBase / PluginsDirectoryName
  private[this] def defaultDependencyBase(globalBase: File) = globalBase / "dependency"
  private[this] def defaultGlobalZinc(globalBase: File) = globalBase / "zinc"

  def configurationSources(base: File): Seq[File] = (base * (GlobFilter("*.sbt") - ".sbt")).get
  def pluginDirectory(definitionBase: File) = definitionBase / PluginsDirectoryName

  def evalOutputDirectory(base: File) = outputDirectory(base) / "config-classes"
  def outputDirectory(base: File) = base / DefaultTargetName

  def projectStandard(base: File) = base / "project"
  def globalLoggingStandard(base: File): File =
    base.getCanonicalFile / DefaultTargetName / GlobalLogging
  def globalTaskDirectoryStandard(base: File): File =
    base.getCanonicalFile / DefaultTargetName / TaskTempDirectory

  final val PluginsDirectoryName = "plugins"
  final val DefaultTargetName = "target"
  final val ConfigDirectoryName = ".sbt"
  final val GlobalBaseProperty = "sbt.global.base"
  final val StagingProperty = "sbt.global.staging"
  final val GlobalPluginsProperty = "sbt.global.plugins"
  final val GlobalSettingsProperty = "sbt.global.settings"
  final val DependencyBaseProperty = "sbt.dependency.base"
  final val GlobalZincProperty = "sbt.global.zinc"
  final val GlobalLogging = "global-logging"
  final val TaskTempDirectory = "task-temp-directory"

  def crossPath(base: File, instance: xsbti.compile.ScalaInstance): File =
    base / ("scala_" + instance.version)
}
