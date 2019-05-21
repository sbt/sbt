/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.inc

import java.io.File

import sbt.internal.inc.classpath.ClassLoaderCache
import sbt.librarymanagement.{ DependencyResolution, ModuleID }
import xsbti._
import xsbti.compile._

object ZincLmUtil {
  import xsbti.compile.ScalaInstance

  /**
   * Instantiate a Scala compiler that is instrumented to analyze dependencies.
   * This Scala compiler is useful to create your own instance of incremental
   * compilation.
   */
  def scalaCompiler(
      scalaInstance: ScalaInstance,
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

  def getDefaultBridgeModule(scalaVersion: String): ModuleID =
    ZincComponentCompiler.getDefaultBridgeModule(scalaVersion)
}
