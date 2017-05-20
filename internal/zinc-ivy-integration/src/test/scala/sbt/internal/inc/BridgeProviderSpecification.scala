package sbt.internal.inc

import java.io.File
import java.util.concurrent.Callable

import sbt.io.IO
import sbt.io.syntax._
import sbt.librarymanagement.{
  DefaultMavenRepository,
  FileRepository,
  Patterns,
  Resolver,
  UpdateOptions
}
import sbt.util.Logger
import xsbti.compile.CompilerBridgeProvider
import xsbti.{ ComponentProvider, GlobalLock }

/**
 * Base class for test suites that must be able to fetch and compile the compiler bridge.
 *
 * This is a very good example on how to instantiate the compiler bridge provider.
 */
abstract class BridgeProviderSpecification extends UnitSpec {
  def currentBase: File = new File(".")
  def currentTarget: File = currentBase / "target" / "ivyhome"
  def currentManaged: File = currentBase / "target" / "lib_managed"

  val resolvers = Array(ZincComponentCompiler.LocalResolver, DefaultMavenRepository)
  private val ivyConfiguration =
    ZincComponentCompiler.getDefaultConfiguration(currentBase, currentTarget, resolvers, log)

  def secondaryCacheDirectory: File = {
    val target = file("target").getAbsoluteFile
    target / "zinc-components"
  }

  def getZincProvider(targetDir: File, log: Logger): CompilerBridgeProvider = {
    val lock = ZincComponentCompiler.getDefaultLock
    val secondaryCache = Some(secondaryCacheDirectory)
    val componentProvider = ZincComponentCompiler.getDefaultComponentProvider(targetDir)
    val manager = new ZincComponentManager(lock, componentProvider, secondaryCache, log)
    ZincComponentCompiler.interfaceProvider(manager, ivyConfiguration, currentManaged)
  }

  def getCompilerBridge(targetDir: File, log: Logger, scalaVersion: String): File = {
    val provider = getZincProvider(targetDir, log)
    val scalaInstance = provider.getScalaInstance(scalaVersion, log)
    val bridge = provider.getCompiledBridge(scalaInstance, log)
    val target = targetDir / s"target-bridge-$scalaVersion.jar"
    IO.copyFile(bridge, target)
    target
  }

  def scalaInstance(scalaVersion: String,
                    targetDir: File,
                    logger: Logger): xsbti.compile.ScalaInstance = {
    val provider = getZincProvider(targetDir, logger)
    provider.getScalaInstance(scalaVersion, logger)
  }
}
