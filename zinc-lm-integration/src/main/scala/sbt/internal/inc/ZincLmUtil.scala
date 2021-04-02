/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.inc

import java.io.File
import sbt.internal.inc.classpath.ClassLoaderCache
import sbt.internal.util.MessageOnlyException
import sbt.librarymanagement.{
  DependencyResolution,
  ModuleID,
  ScalaArtifacts,
  UnresolvedWarningConfiguration,
  UpdateConfiguration
}
import sbt.librarymanagement.syntax._
import xsbti.ArtifactInfo.SbtOrganization
import xsbti._
import xsbti.compile.{ ClasspathOptions, ScalaInstance => XScalaInstance }

object ZincLmUtil {

  /**
   * Instantiate a Scala compiler that is instrumented to analyze dependencies.
   * This Scala compiler is useful to create your own instance of incremental
   * compilation.
   */
  def scalaCompiler(
      scalaInstance: XScalaInstance,
      classpathOptions: ClasspathOptions,
      globalLock: GlobalLock,
      componentProvider: ComponentProvider,
      secondaryCacheDir: Option[File],
      dependencyResolution: DependencyResolution,
      compilerBridgeSource: ModuleID,
      scalaJarsTarget: File,
      classLoaderCache: Option[ClassLoaderCache],
      log: Logger
  ): AnalyzingCompiler = {
    val compilerBridgeProvider = ZincComponentCompiler.interfaceProvider(
      compilerBridgeSource,
      new ZincComponentManager(globalLock, componentProvider, secondaryCacheDir, log),
      dependencyResolution,
      scalaJarsTarget,
    )
    new AnalyzingCompiler(
      scalaInstance,
      compilerBridgeProvider,
      classpathOptions,
      _ => (),
      classLoaderCache
    )
  }

  def fetchDefaultBridgeModule(
      scalaVersion: String,
      dependencyResolution: DependencyResolution,
      updateConfiguration: UpdateConfiguration,
      warningConfig: UnresolvedWarningConfiguration,
      logger: Logger
  ): File = {
    val bridgeModule = getDefaultBridgeModule(scalaVersion)
    val descriptor = dependencyResolution.wrapDependencyInModule(bridgeModule)
    dependencyResolution
      .update(descriptor, updateConfiguration, warningConfig, logger)
      .toOption
      .flatMap { report =>
        val jars = report.select(
          configurationFilter(Compile.name),
          moduleFilter(bridgeModule.organization, bridgeModule.name, bridgeModule.revision),
          artifactFilter(`extension` = "jar", classifier = "")
        )
        if (jars.size > 1)
          sys.error(s"There should be only one jar for $bridgeModule but found $jars")
        else jars.headOption
      }
      .getOrElse(throw new MessageOnlyException(s"Missing $bridgeModule"))
  }

  def getDefaultBridgeModule(scalaVersion: String): ModuleID = {
    if (ScalaArtifacts.isScala3(scalaVersion)) {
      ModuleID(ScalaArtifacts.Organization, "scala3-sbt-bridge", scalaVersion)
        .withConfigurations(Some(Compile.name))
    } else {
      val compilerBridgeId = scalaVersion match {
        case sc if sc startsWith "2.10." => "compiler-bridge_2.10"
        case sc if sc startsWith "2.11." => "compiler-bridge_2.11"
        case sc if sc startsWith "2.12." => "compiler-bridge_2.12"
        case "2.13.0-M1"                 => "compiler-bridge_2.12"
        case _                           => "compiler-bridge_2.13"
      }
      ModuleID(SbtOrganization, compilerBridgeId, ZincComponentManager.version)
        .withConfigurations(Some(Compile.name))
    }
  }

  def getDefaultBridgeSourceModule(scalaVersion: String): ModuleID =
    getDefaultBridgeModule(scalaVersion).sources()
}
