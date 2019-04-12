/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.io.File
import java.net.URLClassLoader

import sbt.librarymanagement.{ DependencyResolution, ModuleID }
import sbt.internal.inc.classpath.ClassLoaderCache
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
      log: Logger
  ): AnalyzingCompiler = {
    val compilerBridgeProvider = ZincComponentCompiler.interfaceProvider(
      compilerBridgeSource,
      new ZincComponentManager(globalLock, componentProvider, secondaryCacheDir, log),
      dependencyResolution,
      scalaJarsTarget,
    )
    val loader = Some(new ClassLoaderCache(new URLClassLoader(new Array(0))))
    new AnalyzingCompiler(scalaInstance, compilerBridgeProvider, classpathOptions, _ => (), loader)
  }

  def getDefaultBridgeModule(scalaVersion: String): ModuleID =
    ZincComponentCompiler.getDefaultBridgeModule(scalaVersion)
}
