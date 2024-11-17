/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.inc

import java.io.File
import java.net.URLClassLoader

import sbt.io.IO
import sbt.io.syntax._
import sbt.librarymanagement._
import sbt.librarymanagement.ivy._
import sbt.util.Logger
import xsbti.compile.CompilerBridgeProvider
import org.scalatest._
import org.scalatest.matchers.should.Matchers

/**
 * Base class for test suites that must be able to fetch and compile the compiler bridge.
 *
 * This is a very good example on how to instantiate the compiler bridge provider.
 */
abstract class IvyBridgeProviderSpecification
    extends flatspec.FixtureAnyFlatSpec
    with fixture.TestDataFixture
    with Matchers {
  def currentBase: File = new File(".")
  def currentTarget: File = currentBase / "target" / "ivyhome"
  def currentManaged: File = currentBase / "target" / "lib_managed"
  def secondaryCacheDirectory: File = file("target").getAbsoluteFile / "zinc-components"

  val resolvers = Array(
    ZincComponentCompiler.LocalResolver: Resolver,
    Resolver.mavenCentral: Resolver,
    MavenRepository(
      "scala-integration",
      "https://scala-ci.typesafe.com/artifactory/scala-integration/"
    ): Resolver,
  )

  private def ivyConfiguration(log: Logger) =
    getDefaultConfiguration(currentBase, currentTarget, resolvers, log)

  def getZincProvider(bridge: ModuleID, targetDir: File, log: Logger): CompilerBridgeProvider = {
    val lock = ZincComponentCompiler.getDefaultLock
    val secondaryCache = Some(secondaryCacheDirectory)
    val componentProvider = ZincComponentCompiler.getDefaultComponentProvider(targetDir)
    val manager = new ZincComponentManager(lock, componentProvider, secondaryCache, log)
    val dependencyResolution = IvyDependencyResolution(ivyConfiguration(log))
    ZincComponentCompiler.interfaceProvider(bridge, manager, dependencyResolution, currentManaged)
  }

  def getCompilerBridge(
      targetDir: File,
      log: Logger,
      scalaVersion: String,
  )(using td: TestData): File = {
    val zincVersion = td.configMap.get("sbt.zinc.version") match {
      case Some(v: String) => v
      case _               => throw new IllegalStateException("No zinc version specified")
    }
    val bridge0 = ZincLmUtil.getDefaultBridgeSourceModule(scalaVersion)
    // redefine the compiler bridge version
    // using the version of zinc used during testing
    // this way when building with zinc as a source dependency
    // these specs don't go looking for some SHA-suffixed compiler bridge
    val bridge1 = bridge0.withRevision(zincVersion)
    val provider = getZincProvider(bridge1, targetDir, log)
    val scalaInstance = provider.fetchScalaInstance(scalaVersion, log)
    val bridge = provider.fetchCompiledBridge(scalaInstance, log)
    scalaInstance.loader.asInstanceOf[URLClassLoader].close()
    scalaInstance.loaderLibraryOnly.asInstanceOf[URLClassLoader].close()
    val target = targetDir / s"target-bridge-$scalaVersion.jar"
    IO.copyFile(bridge, target)
    target
  }

  private def getDefaultConfiguration(
      baseDirectory: File,
      ivyHome: File,
      resolvers0: Array[Resolver],
      log: xsbti.Logger,
  ): InlineIvyConfiguration = {
    val resolvers = resolvers0.toVector
    val chainResolver = ChainedResolver("zinc-chain", resolvers)
    InlineIvyConfiguration()
      .withPaths(IvyPaths(baseDirectory.toString, Some(ivyHome.toString)))
      .withResolvers(resolvers)
      .withModuleConfigurations(Vector(ModuleConfiguration("*", chainResolver)))
      .withLock(None)
      .withChecksums(Vector.empty)
      .withResolutionCacheDir(ivyHome / "resolution-cache")
      .withUpdateOptions(UpdateOptions())
      .withLog(log)
  }
}
