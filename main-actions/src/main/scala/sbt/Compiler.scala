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

  /** Inputs necessary to run the incremental compiler. */
  // final case class Inputs(compilers: Compilers, config: Options, incSetup: IncSetup)
  // /** The inputs for the compiler *and* the previous analysis of source dependencies. */
  // final case class InputsWithPrevious(inputs: Inputs, previousAnalysis: PreviousAnalysis)
  // final case class Options(classpath: Seq[File], sources: Seq[File], classesDirectory: File, options: Seq[String], javacOptions: Seq[String], maxErrors: Int, sourcePositionMapper: Position => Position, order: CompileOrder)
  // final case class IncSetup(analysisMap: File => Option[Analysis], definesClass: DefinesClass, skip: Boolean, cacheFile: File, cache: GlobalsCache, incOptions: IncOptions)

  // private[sbt] trait JavaToolWithNewInterface extends JavaTool {
  //   def newJavac: IncrementalCompilerJavaTools
  // }
  /** The instances of Scalac/Javac used to compile the current project. */
  // final case class Compilers(scalac: AnalyzingCompiler, javac: IncrementalCompilerJavaTools)

  /** The previous source dependency analysis result from compilation. */
  // final case class PreviousAnalysis(analysis: Analysis, setup: Option[MiniSetup])

  // def inputs(classpath: Seq[File], sources: Seq[File], classesDirectory: File, options: Seq[String],
  //   javacOptions: Seq[String], maxErrors: Int, sourcePositionMappers: Seq[Position => Option[Position]],
  //   order: CompileOrder)(implicit compilers: Compilers, incSetup: IncSetup, log: Logger): Inputs =
  //   new Inputs(
  //     compilers,
  //     new Options(classpath, sources, classesDirectory, options, javacOptions, maxErrors, foldMappers(sourcePositionMappers), order),
  //     incSetup
  //   )

  // @deprecated("Use `compilers(ScalaInstance, ClasspathOptions, Option[File], IvyConfiguration)`.", "0.13.10")
  // def compilers(instance: ScalaInstance, cpOptions: ClasspathOptions, javaHome: Option[File])(implicit app: AppConfiguration, log: Logger): Compilers =
  //   {
  //     val javac =
  //       AggressiveCompile.directOrFork(instance, cpOptions, javaHome)
  //     val javac2 =
  //       JavaTools.directOrFork(instance, cpOptions, javaHome)
  //     // Hackery to enable both the new and deprecated APIs to coexist peacefully.
  //     case class CheaterJavaTool(newJavac: IncrementalCompilerJavaTools, delegate: JavaTool) extends JavaTool with JavaToolWithNewInterface {
  //       def compile(contract: JavacContract, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String])(implicit log: Logger): Unit =
  //         javac.compile(contract, sources, classpath, outputDirectory, options)(log)
  //       def onArgs(f: Seq[String] => Unit): JavaTool = CheaterJavaTool(newJavac, delegate.onArgs(f))
  //     }
  //     compilers(instance, cpOptions, CheaterJavaTool(javac2, javac))
  //   }
  // def compilers(instance: ScalaInstance, cpOptions: ClasspathOptions, javaHome: Option[File], ivyConfiguration: IvyConfiguration)(implicit app: AppConfiguration, log: Logger): Compilers =
  //   {
  //     val javac =
  //       AggressiveCompile.directOrFork(instance, cpOptions, javaHome)
  //     val javac2 =
  //       JavaTools.directOrFork(instance, cpOptions, javaHome)
  //     // Hackery to enable both the new and deprecated APIs to coexist peacefully.
  //     case class CheaterJavaTool(newJavac: IncrementalCompilerJavaTools, delegate: JavaTool) extends JavaTool with JavaToolWithNewInterface {
  //       def compile(contract: JavacContract, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String])(implicit log: Logger): Unit =
  //         javac.compile(contract, sources, classpath, outputDirectory, options)(log)
  //       def onArgs(f: Seq[String] => Unit): JavaTool = CheaterJavaTool(newJavac, delegate.onArgs(f))
  //     }
  //     val scalac = scalaCompiler(instance, cpOptions, ivyConfiguration)
  //     new Compilers(scalac, CheaterJavaTool(javac2, javac))
  //   }
  // @deprecated("Deprecated in favor of new sbt.compiler.javac package.", "0.13.8")
  // def compilers(instance: ScalaInstance, cpOptions: ClasspathOptions, javac: sbt.compiler.JavaCompiler.Fork)(implicit app: AppConfiguration, log: Logger): Compilers =
  //   {
  //     val javaCompiler = sbt.compiler.JavaCompiler.fork(cpOptions, instance)(javac)
  //     compilers(instance, cpOptions, javaCompiler)
  //   }
  // @deprecated("Deprecated in favor of new sbt.compiler.javac package.", "0.13.8")
  // def compilers(instance: ScalaInstance, cpOptions: ClasspathOptions, javac: JavaTool)(implicit app: AppConfiguration, log: Logger): Compilers =
  //   {
  //     val scalac = scalaCompiler(instance, cpOptions)
  //     new Compilers(scalac, javac)
  //   }
  // @deprecated("Use `scalaCompiler(ScalaInstance, ClasspathOptions, IvyConfiguration)`.", "0.13.10")
  // def scalaCompiler(instance: ScalaInstance, cpOptions: ClasspathOptions)(implicit app: AppConfiguration, log: Logger): AnalyzingCompiler =
  //   {
  //     val launcher = app.provider.scalaProvider.launcher
  //     val componentManager = new ComponentManager(launcher.globalLock, app.provider.components, Option(launcher.ivyHome), log)
  //     val provider = ComponentCompiler.interfaceProvider(componentManager)
  //     new AnalyzingCompiler(instance, provider, cpOptions)
  //   }

  def compilers(cpOptions: ClasspathOptions, ivyConfiguration: IvyConfiguration, fileToStore: File => CacheStore)(implicit app: AppConfiguration, log: Logger): Compilers =
    {
      val scalaProvider = app.provider.scalaProvider
      val instance = ScalaInstance(scalaProvider.version, scalaProvider.launcher)
      val sourceModule = scalaCompilerBridgeSource2_12
      compilers(instance, cpOptions, None, ivyConfiguration, fileToStore, sourceModule)
    }

  // def compilers(instance: ScalaInstance, cpOptions: ClasspathOptions)(implicit app: AppConfiguration, log: Logger): Compilers =
  //   compilers(instance, cpOptions, None)

  // TODO: Get java compiler
  def compilers(instance: ScalaInstance, cpOptions: ClasspathOptions, javaHome: Option[File],
    ivyConfiguration: IvyConfiguration, fileToStore: File => CacheStore, sourcesModule: ModuleID)(implicit app: AppConfiguration, log: Logger): Compilers = {
    val scalac = scalaCompiler(instance, cpOptions, javaHome, ivyConfiguration, fileToStore, sourcesModule)
    val javac = JavaTools.directOrFork(instance, cpOptions, javaHome)
    new Compilers(scalac, javac)
  }
  def scalaCompiler(instance: ScalaInstance, cpOptions: ClasspathOptions, javaHome: Option[File], ivyConfiguration: IvyConfiguration, fileToStore: File => CacheStore, sourcesModule: ModuleID)(implicit app: AppConfiguration, log: Logger): AnalyzingCompiler =
    {
      val launcher = app.provider.scalaProvider.launcher
      val componentManager = new ZincComponentManager(launcher.globalLock, app.provider.components, Option(launcher.ivyHome), log)
      val provider = ComponentCompiler.interfaceProvider(componentManager, ivyConfiguration, fileToStore, sourcesModule)
      new AnalyzingCompiler(instance, provider, cpOptions, _ => (), None)
    }

  private val compiler = new IncrementalCompilerImpl
  def compile(in: Inputs, log: Logger): CompileResult =
    {
      compiler.compile(in, log)
      // import in.inputs.config._
      // compile(in, log, new LoggerReporter(maxErrors, log, sourcePositionMapper))
    }
  // def compile(in: Inputs, log: Logger, reporter: xsbti.Reporter): CompileResult =
  //   {
  //     import in.inputs.compilers._
  //     import in.inputs.config._
  //     import in.inputs.incSetup._
  //     // Here is some trickery to choose the more recent (reporter-using) java compiler rather
  //     // than the previously defined versions.
  //     // TODO - Remove this hackery in sbt 1.0.
  //     val javacChosen: xsbti.compile.JavaCompiler =
  //       in.inputs.compilers.javac.xsbtiCompiler // ).getOrElse(in.inputs.compilers.javac)
  //     // TODO - Why are we not using the IC interface???
  //     val compiler = new IncrementalCompilerImpl
  //     compiler.incrementalCompile(scalac, javacChosen, sources, classpath, CompileOutput(classesDirectory), cache, None, options, javacOptions,
  //       in.previousAnalysis.analysis, in.previousAnalysis.setup, analysisMap, definesClass, reporter, order, skip, incOptions)(log)
  //   }

  private[sbt] def foldMappers[A](mappers: Seq[A => Option[A]]) =
    mappers.foldRight({ p: A => p }) { (mapper, mappers) => { p: A => mapper(p).getOrElse(mappers(p)) } }
}
