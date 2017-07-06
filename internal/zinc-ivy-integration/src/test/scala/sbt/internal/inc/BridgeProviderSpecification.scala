package sbt.internal.inc

import java.io.File

import sbt.io.IO
import sbt.io.syntax._
import sbt.librarymanagement.{
  DefaultMavenRepository,
  ChainedResolver,
  IvyLibraryManagement,
  ModuleConfiguration,
  Resolver,
  UpdateOptions
}
import sbt.internal.librarymanagement.{ InlineIvyConfiguration, IvyPaths }
import sbt.util.Logger
import xsbti.compile.CompilerBridgeProvider

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
    getDefaultConfiguration(currentBase, currentTarget, resolvers, log)

  def secondaryCacheDirectory: File = {
    val target = file("target").getAbsoluteFile
    target / "zinc-components"
  }

  def getZincProvider(targetDir: File, log: Logger): CompilerBridgeProvider = {
    val lock = ZincComponentCompiler.getDefaultLock
    val secondaryCache = Some(secondaryCacheDirectory)
    val componentProvider = ZincComponentCompiler.getDefaultComponentProvider(targetDir)
    val manager = new ZincComponentManager(lock, componentProvider, secondaryCache, log)
    val libraryManagement = new IvyLibraryManagement(ivyConfiguration)
    ZincComponentCompiler.interfaceProvider(manager, libraryManagement, currentManaged)
  }

  def getCompilerBridge(targetDir: File, log: Logger, scalaVersion: String): File = {
    val provider = getZincProvider(targetDir, log)
    val scalaInstance = provider.fetchScalaInstance(scalaVersion, log)
    val bridge = provider.fetchCompiledBridge(scalaInstance, log)
    val target = targetDir / s"target-bridge-$scalaVersion.jar"
    IO.copyFile(bridge, target)
    target
  }

  def scalaInstance(scalaVersion: String,
                    targetDir: File,
                    logger: Logger): xsbti.compile.ScalaInstance = {
    val provider = getZincProvider(targetDir, logger)
    provider.fetchScalaInstance(scalaVersion, logger)
  }

  private def getDefaultConfiguration(baseDirectory: File,
                                      ivyHome: File,
                                      resolvers0: Array[Resolver],
                                      log: xsbti.Logger): InlineIvyConfiguration = {
    import sbt.io.syntax._
    val resolvers = resolvers0.toVector
    val chainResolver = ChainedResolver("zinc-chain", resolvers)
    new InlineIvyConfiguration(
      paths = IvyPaths(baseDirectory, Some(ivyHome)),
      resolvers = resolvers,
      otherResolvers = Vector.empty,
      moduleConfigurations = Vector(ModuleConfiguration("*", chainResolver)),
      lock = None,
      checksums = Vector.empty,
      managedChecksums = false,
      resolutionCacheDir = Some(ivyHome / "resolution-cache"),
      updateOptions = UpdateOptions(),
      log = log
    )
  }
}
