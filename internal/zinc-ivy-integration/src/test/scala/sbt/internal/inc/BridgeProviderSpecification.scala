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
 */
abstract class BridgeProviderSpecification extends BaseIvySpecification {
  def realLocal: Resolver = {
    val pList = Vector(s"$${user.home}/.ivy2/local/${Resolver.localBasePattern}")
    FileRepository(
      "local",
      Resolver.defaultFileConfiguration,
      Patterns().withIvyPatterns(pList).withArtifactPatterns(pList).withIsMavenCompatible(false))
  }

  override def resolvers: Vector[Resolver] = Vector(realLocal, DefaultMavenRepository)
  private val ivyConfiguration = mkIvyConfiguration(UpdateOptions())

  def secondaryCacheDirectory: File = {
    val target = file("target").getAbsoluteFile
    target / "zinc-components"
  }

  def getZincProvider(targetDir: File, log: Logger): CompilerBridgeProvider = {
    val secondaryCache = Some(secondaryCacheDirectory)
    val manager = new ZincComponentManager(lock, compProvider(targetDir), secondaryCache, log)
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

  private val lock: GlobalLock = new GlobalLock {
    override def apply[T](file: File, callable: Callable[T]): T = callable.call()
  }

  private def compProvider(targetDir: File): ComponentProvider = new ComponentProvider {
    override def lockFile(): File = targetDir / "lock"
    override def componentLocation(id: String): File =
      throw new UnsupportedOperationException
    override def component(componentID: String): Array[File] =
      IO.listFiles(targetDir / componentID)
    override def defineComponent(componentID: String, files: Array[File]): Unit =
      files.foreach(f => IO.copyFile(f, targetDir / componentID / f.getName))
    override def addToComponent(componentID: String, files: Array[File]): Boolean = {
      defineComponent(componentID, files)
      true
    }
  }
}
