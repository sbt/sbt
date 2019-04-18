/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.inc

import java.io.File

import sbt.io.IO
import sbt.io.syntax._
import sbt.librarymanagement._
import sbt.librarymanagement.ivy._
import sbt.util.Logger
import xsbti.compile.CompilerBridgeProvider
import org.scalatest._

/**
 * Base class for test suites that must be able to fetch and compile the compiler bridge.
 *
 * This is a very good example on how to instantiate the compiler bridge provider.
 */
abstract class IvyBridgeProviderSpecification extends FlatSpec with Matchers {
  def currentBase: File = new File(".")
  def currentTarget: File = currentBase / "target" / "ivyhome"
  def currentManaged: File = currentBase / "target" / "lib_managed"

  val resolvers = Array(
    ZincComponentCompiler.LocalResolver,
    Resolver.mavenCentral,
    MavenRepository("scala-integration",
                    "https://scala-ci.typesafe.com/artifactory/scala-integration/")
  )

  private def ivyConfiguration(log: Logger) =
    getDefaultConfiguration(currentBase, currentTarget, resolvers, log)

  def secondaryCacheDirectory: File = {
    val target = file("target").getAbsoluteFile
    target / "zinc-components"
  }

  def getZincProvider(bridge: ModuleID, targetDir: File, log: Logger): CompilerBridgeProvider = {
    val lock = ZincComponentCompiler.getDefaultLock
    val secondaryCache = Some(secondaryCacheDirectory)
    val componentProvider = ZincComponentCompiler.getDefaultComponentProvider(targetDir)
    val manager = new ZincComponentManager(lock, componentProvider, secondaryCache, log)
    val dependencyResolution = IvyDependencyResolution(ivyConfiguration(log))
    ZincComponentCompiler.interfaceProvider(bridge, manager, dependencyResolution, currentManaged)
  }

  def getCompilerBridge(targetDir: File, log: Logger, scalaVersion: String): File = {
    val bridge0 = ZincComponentCompiler.getDefaultBridgeModule(scalaVersion)
    // redefine the compiler bridge version
    // using the version of zinc used during testing
    // this way when building with zinc as a source dependency
    // these specs don't go looking for some SHA-suffixed compiler bridge
    val bridge1 = bridge0.withRevision(ZincLmIntegrationBuildInfo.zincVersion)
    val provider = getZincProvider(bridge1, targetDir, log)
    val scalaInstance = provider.fetchScalaInstance(scalaVersion, log)
    val bridge = provider.fetchCompiledBridge(scalaInstance, log)
    val target = targetDir / s"target-bridge-$scalaVersion.jar"
    IO.copyFile(bridge, target)
    target
  }

  private def getDefaultConfiguration(baseDirectory: File,
                                      ivyHome: File,
                                      resolvers0: Array[Resolver],
                                      log: xsbti.Logger): InlineIvyConfiguration = {
    import sbt.io.syntax._
    val resolvers = resolvers0.toVector
    val chainResolver = ChainedResolver("zinc-chain", resolvers)
    InlineIvyConfiguration()
      .withPaths(IvyPaths(baseDirectory, Some(ivyHome)))
      .withResolvers(resolvers)
      .withModuleConfigurations(Vector(ModuleConfiguration("*", chainResolver)))
      .withLock(None)
      .withChecksums(Vector.empty)
      .withResolutionCacheDir(ivyHome / "resolution-cache")
      .withUpdateOptions(UpdateOptions())
      .withLog(log)
  }
}
