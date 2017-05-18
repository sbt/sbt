/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.io.File

import sbt.io.{ Hash, IO }
import sbt.internal.librarymanagement.{
  InlineConfiguration,
  IvyActions,
  IvyConfiguration,
  IvySbt,
  JsonUtil,
  LogicalClock,
  RetrieveConfiguration,
  UnresolvedWarning,
  UnresolvedWarningConfiguration
}
import sbt.librarymanagement.{
  ArtifactTypeFilter,
  Configurations,
  ModuleID,
  ModuleInfo,
  Resolver,
  UpdateConfiguration,
  UpdateLogging,
  UpdateOptions
}
import sbt.librarymanagement.syntax._
import sbt.util.{ InterfaceUtil, Logger }
import xsbti.compile.CompilerBridgeProvider

private[sbt] object ZincComponentCompiler {
  final val binSeparator = "-bin_"
  final val javaClassVersion = System.getProperty("java.class.version")

  private[inc] final val sbtOrgTemp = JsonUtil.sbtOrgTemp
  private[inc] final val modulePrefixTemp = "temp-module-"

  private final val ZincVersionPropertyFile = "/incrementalcompiler.version.properties"
  private final val ZincVersionProperty = "version"
  final lazy val incrementalVersion: String = {
    val cl = this.getClass.getClassLoader
    ResourceLoader.getPropertiesFor(ZincVersionPropertyFile, cl).getProperty(ZincVersionProperty)
  }

  private class ZincCompilerBridgeProvider(manager: ZincComponentManager,
                                           ivyConfiguration: IvyConfiguration,
                                           bridgeModule: ModuleID)
      extends CompilerBridgeProvider {

    override def getBridgeSources(scalaInstance: xsbti.compile.ScalaInstance,
                                  logger: xsbti.Logger): File = {
      val autoClasspath = ClasspathOptionsUtil.auto
      val bridgeCompiler = new RawCompiler(scalaInstance, autoClasspath, logger)
      val ivyComponent =
        new ZincComponentCompiler(bridgeCompiler, manager, ivyConfiguration, bridgeModule, logger)
      logger.debug(InterfaceUtil.f0(s"Getting $bridgeModule for Scala ${scalaInstance.version}"))
      ivyComponent.getCompiledBridgeJar
    }
  }

  def interfaceProvider(manager: ZincComponentManager,
                        ivyConfiguration: IvyConfiguration,
                        sourcesModule: ModuleID): CompilerBridgeProvider =
    new ZincCompilerBridgeProvider(manager, ivyConfiguration, sourcesModule)

}

/**
 * Component compiler which is able to to retrieve the compiler bridge sources
 * `sourceModule` using Ivy.
 * The compiled classes are cached using the provided component manager according
 * to the actualVersion field of the RawCompiler.
 */
private[inc] class ZincComponentCompiler(
    compiler: RawCompiler,
    manager: ZincComponentManager,
    ivyConfiguration: IvyConfiguration,
    bridgeSources: ModuleID,
    log: Logger
) {
  import sbt.internal.util.{ BufferedLogger, FullLogger }
  private final val ivySbt: IvySbt = new IvySbt(ivyConfiguration)
  private final val buffered = new BufferedLogger(FullLogger(log))

  def getCompiledBridgeJar: File = {
    val jarBinaryName = createBridgeSourcesID(bridgeSources)
    manager.file(jarBinaryName)(IfMissing.define(true, compileAndInstall(jarBinaryName)))
  }

  /**
   * Returns the id for the compiler interface component.
   *
   * The ID contains the following parts:
   *   - The organization, name and revision.
   *   - The bin separator to make clear the jar represents binaries.
   *   - The Scala version for which the compiler interface is meant to.
   *   - The JVM class version.
   *
   * Example: "org.scala-sbt-compiler-bridge-1.0.0-bin_2.11.7__50.0".
   *
   * @param sources The moduleID representing the compiler bridge sources.
   * @return The complete jar identifier for the bridge sources.
   */
  private def createBridgeSourcesID(sources: ModuleID): String = {
    import ZincComponentCompiler.{ binSeparator, javaClassVersion }
    val id = s"${sources.organization}-${sources.name}-${sources.revision}"
    val scalaVersion = compiler.scalaInstance.actualVersion()
    s"$id$binSeparator${scalaVersion}__$javaClassVersion"
  }

  /**
   * Resolves the compiler bridge sources, compiles them and installs the sbt component
   * in the local filesystem to make sure that it's reused the next time is required.
   *
   * @param compilerBridgeId The identifier for the compiler bridge sources.
   */
  private def compileAndInstall(compilerBridgeId: String): Unit = {
    import UnresolvedWarning.unresolvedWarningLines
    val ivyModuleForBridge = wrapDependencyInModule(bridgeSources)
    IO.withTemporaryDirectory { binaryDirectory =>
      val target = new File(binaryDirectory, s"$compilerBridgeId.jar")
      buffered bufferQuietly {
        IO.withTemporaryDirectory { retrieveDirectory =>
          update(ivyModuleForBridge, retrieveDirectory) match {
            case Left(uw) =>
              val mod = bridgeSources.toString
              val unresolvedLines = unresolvedWarningLines.showLines(uw)
              val unretrievedMessage = s"The compiler bridge sources $mod could not be retrieved."
              throw new InvalidComponent(s"$unresolvedLines\n$unresolvedLines")

            case Right(allArtifacts) =>
              val (srcs, xsbtiJars) = allArtifacts.partition(_.getName.endsWith("-sources.jar"))
              val toCompileID = bridgeSources.name
              AnalyzingCompiler.compileSources(srcs, target, xsbtiJars, toCompileID, compiler, log)
              manager.define(compilerBridgeId, Seq(target))
          }
        }

      }
    }
  }

  /**
   * Returns an ivy module that will wrap and download a given `moduleID`.
   *
   * @param moduleID The `moduleID` that needs to be wrapped in a dummy module to be downloaded.
   */
  private def wrapDependencyInModule(moduleID: ModuleID): ivySbt.Module = {
    def getModule(moduleID: ModuleID, deps: Vector[ModuleID], uo: UpdateOptions): ivySbt.Module = {
      val moduleInfo = ModuleInfo(moduleID.name)
      val componentIvySettings = InlineConfiguration(
        validate = false,
        ivyScala = None,
        module = moduleID,
        moduleInfo = moduleInfo,
        dependencies = deps
      ).withConfigurations(Vector(Configurations.Component))
      new ivySbt.Module(componentIvySettings)
    }

    import ZincComponentCompiler.{ sbtOrgTemp, modulePrefixTemp }
    val sha1 = Hash.toHex(Hash(moduleID.name))
    val dummyID = ModuleID(sbtOrgTemp, s"$modulePrefixTemp$sha1", moduleID.revision)
      .withConfigurations(moduleID.configurations)
    val defaultUpdateOptions = UpdateOptions()
    getModule(dummyID, Vector(moduleID), defaultUpdateOptions)
  }

  // The implementation of this method is linked to `wrapDependencyInModule`
  private def prettyPrintDependency(module: ivySbt.Module): String = {
    module.moduleSettings match {
      case ic: InlineConfiguration =>
        // Pretty print the module as `ModuleIDExtra.toStringImpl` does.
        val dependency =
          ic.dependencies.map(m => s"${m.organization}:${m.name}:${m.revision}").headOption
        dependency.getOrElse(sys.error("Fatal: more than one dependency in dummy bridge module."))
      case _ => sys.error("Fatal: configuration to download was not inline.")
    }
  }

  private final val warningConf = UnresolvedWarningConfiguration()
  private final val defaultRetrievePattern = Resolver.defaultRetrievePattern
  private def defaultUpdateConfiguration(retrieveDirectory: File): UpdateConfiguration = {
    val retrieve = RetrieveConfiguration(retrieveDirectory, defaultRetrievePattern, false, None)
    val logLevel = UpdateLogging.DownloadOnly
    val noDocs = ArtifactTypeFilter.forbid(Set("doc"))
    UpdateConfiguration(Some(retrieve), missingOk = false, logLevel, noDocs)
  }

  private def update(module: ivySbt.Module,
                     retrieveDirectory: File): Either[UnresolvedWarning, Vector[File]] = {
    import IvyActions.updateEither
    val updateConfiguration = defaultUpdateConfiguration(retrieveDirectory)
    val dependencies = prettyPrintDependency(module)
    buffered.info(s"Attempting to fetch $dependencies. This operation may fail.")
    val clockForCache = LogicalClock.unknown
    updateEither(module, updateConfiguration, warningConf, clockForCache, None, buffered) match {
      case Left(unresolvedWarning) =>
        buffered.debug(s"Couldn't retrieve module ${prettyPrintDependency(module)}.")
        Left(unresolvedWarning)

      case Right(updateReport) =>
        val allFiles = updateReport.allFiles
        buffered.debug(s"Files retrieved for ${prettyPrintDependency(module)}:")
        buffered.debug(allFiles mkString ", ")
        Right(allFiles)
    }
  }
}
