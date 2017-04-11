/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

import sbt.internal.inc.javac.JavaTools
import sbt.internal.inc.{ AnalyzingCompiler, ComponentCompiler, ScalaInstance, ZincComponentManager, IncrementalCompilerImpl }
import xsbti.{ Logger => _, _ }
import xsbti.compile.{ ClasspathOptions, Compilers, CompileResult, Inputs }
import java.io.File

import sbt.internal.librarymanagement.IvyConfiguration
import sbt.librarymanagement.{ ModuleID, VersionNumber }
import sbt.util.Logger
import sbt.internal.util.CacheStore

object Compiler {
  val DefaultMaxErrors = 100

  private[sbt] def defaultCompilerBridgeSource(sv: String): ModuleID =
    VersionNumber(sv) match {
      // 2.10 and before
      case VersionNumber(ns, _, _) if (ns.size == 3) && (ns(0) == 2) && (ns(1) <= 10) => scalaCompilerBridgeSource2_10
      // 2.11
      case VersionNumber(ns, _, _) if (ns.size == 3) && (ns(0) == 2) && (ns(1) == 11) => scalaCompilerBridgeSource2_11
      case _                                                                          => scalaCompilerBridgeSource2_12
    }

  private[sbt] def scalaCompilerBridgeSource2_10: ModuleID =
    ModuleID(xsbti.ArtifactInfo.SbtOrganization, "compiler-bridge_2.10",
      ComponentCompiler.incrementalVersion).withConfigurations(Some("component")).sources()
  private[sbt] def scalaCompilerBridgeSource2_11: ModuleID =
    ModuleID(xsbti.ArtifactInfo.SbtOrganization, "compiler-bridge_2.11",
      ComponentCompiler.incrementalVersion).withConfigurations(Some("component")).sources()
  private[sbt] def scalaCompilerBridgeSource2_12: ModuleID =
    ModuleID(xsbti.ArtifactInfo.SbtOrganization, "compiler-bridge_2.12",
      ComponentCompiler.incrementalVersion).withConfigurations(Some("component")).sources()

  def compilers(
    cpOptions: ClasspathOptions, ivyConfiguration: IvyConfiguration, fileToStore: File => CacheStore
  )(implicit app: AppConfiguration, log: Logger): Compilers = {
    val scalaProvider = app.provider.scalaProvider
    val instance = ScalaInstance(scalaProvider.version, scalaProvider.launcher)
    val sourceModule = scalaCompilerBridgeSource2_12
    compilers(instance, cpOptions, None, ivyConfiguration, fileToStore, sourceModule)
  }

  // TODO: Get java compiler
  def compilers(
    instance: ScalaInstance, cpOptions: ClasspathOptions, javaHome: Option[File],
    ivyConfiguration: IvyConfiguration, fileToStore: File => CacheStore, sourcesModule: ModuleID
  )(implicit app: AppConfiguration, log: Logger): Compilers = {
    val scalac = scalaCompiler(instance, cpOptions, javaHome, ivyConfiguration, fileToStore, sourcesModule)
    val javac = JavaTools.directOrFork(instance, cpOptions, javaHome)
    new Compilers(scalac, javac)
  }

  def scalaCompiler(
    instance: ScalaInstance, cpOptions: ClasspathOptions, javaHome: Option[File],
    ivyConfiguration: IvyConfiguration, fileToStore: File => CacheStore, sourcesModule: ModuleID
  )(implicit app: AppConfiguration, log: Logger): AnalyzingCompiler = {
    val launcher = app.provider.scalaProvider.launcher
    val componentManager = new ZincComponentManager(launcher.globalLock, app.provider.components, Option(launcher.ivyHome), log)
    val provider = ComponentCompiler.interfaceProvider(componentManager, ivyConfiguration, fileToStore, sourcesModule)
    new AnalyzingCompiler(instance, provider, cpOptions, _ => (), None)
  }

  private val compiler = new IncrementalCompilerImpl

  def compile(in: Inputs, log: Logger): CompileResult = compiler.compile(in, log)

  private[sbt] def foldMappers[A](mappers: Seq[A => Option[A]]) =
    mappers.foldRight({ p: A => p }) { (mapper, mappers) => { p: A => mapper(p).getOrElse(mappers(p)) } }
}
