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
import sbt.io.syntax.*
import sbt.librarymanagement.*
import sbt.util.Logger
import xsbti.compile.CompilerBridgeProvider
import lmcoursier.*
import verify.BasicTestSuite

/**
 * Base class for test suites that must be able to fetch and compile the compiler bridge.
 *
 * This is a very good example on how to instantiate the compiler bridge provider.
 */
trait BridgeProviderSpecification extends BasicTestSuite:
  def currentBase: File = new File(".")
  def currentTarget: File = currentBase / "target" / "ivyhome"
  def currentManaged: File = currentBase / "target" / "lib_managed"
  def secondaryCacheDirectory: File = file("target").getAbsoluteFile / "zinc-components"

  val resolvers = Vector(
    ZincComponentCompiler.LocalResolver: Resolver,
    Resolver.mavenCentral: Resolver,
  )

  def getZincProvider(bridge: ModuleID, targetDir: File, log: Logger): CompilerBridgeProvider =
    val lock = ZincComponentCompiler.getDefaultLock
    val secondaryCache = Some(secondaryCacheDirectory)
    val componentProvider = ZincComponentCompiler.getDefaultComponentProvider(targetDir)
    val manager = new ZincComponentManager(lock, componentProvider, secondaryCache, log)
    val dependencyResolution = CoursierDependencyResolution(
      CoursierConfiguration().withResolvers(resolvers)
    )
    // IvyDependencyResolution(ivyConfiguration(log))
    ZincComponentCompiler.interfaceProvider(bridge, manager, dependencyResolution, currentManaged)

  def getCompilerBridge(
      targetDir: File,
      log: Logger,
      scalaVersion: String,
  ): File =
    val bridge0 = ZincLmUtil.getDefaultBridgeSourceModule(ScalaArtifacts.Organization, scalaVersion)
    // redefine the compiler bridge version
    // using the version of zinc used during testing
    // this way when building with zinc as a source dependency
    // these specs don't go looking for some SHA-suffixed compiler bridge
    val bridge1 = bridge0.withRevision(ZincTestInfo.zincVersion)
    val provider = getZincProvider(bridge1, targetDir, log)
    val scalaInstance = provider.fetchScalaInstance(scalaVersion, log)
    val bridge = provider.fetchCompiledBridge(scalaInstance, log)
    scalaInstance.loader.asInstanceOf[URLClassLoader].close()
    scalaInstance.loaderLibraryOnly.asInstanceOf[URLClassLoader].close()
    val target = targetDir / s"target-bridge-$scalaVersion.jar"
    IO.copyFile(bridge, target)
    target
  end getCompilerBridge
end BridgeProviderSpecification
