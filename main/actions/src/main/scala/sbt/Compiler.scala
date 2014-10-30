/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

import xsbti.{ Logger => _, _ }
import xsbti.compile.{ CompileOrder, GlobalsCache }
import CompileOrder.{ JavaThenScala, Mixed, ScalaThenJava }
import compiler._
import inc._
import Locate.DefinesClass
import java.io.File

object Compiler {
  val DefaultMaxErrors = 100

  final case class Inputs(compilers: Compilers, config: Options, incSetup: IncSetup, previousAnalysis: PreviousAnalysis)
  final case class Options(classpath: Seq[File], sources: Seq[File], classesDirectory: File, options: Seq[String], javacOptions: Seq[String], maxErrors: Int, sourcePositionMapper: Position => Position, order: CompileOrder)
  final case class IncSetup(analysisMap: File => Option[Analysis], definesClass: DefinesClass, skip: Boolean, cacheFile: File, cache: GlobalsCache, incOptions: IncOptions)
  final case class Compilers(scalac: AnalyzingCompiler, javac: JavaTool)
  final case class PreviousAnalysis(analysis: Analysis, setup: Option[CompileSetup])
  final case class AnalysisResult(analysis: Analysis, setup: CompileSetup, modified: Boolean)

  def inputs(classpath: Seq[File], sources: Seq[File], classesDirectory: File, options: Seq[String],
    javacOptions: Seq[String], maxErrors: Int, sourcePositionMappers: Seq[Position => Option[Position]],
    order: CompileOrder, previousAnalysis: PreviousAnalysis)(implicit compilers: Compilers, incSetup: IncSetup, log: Logger): Inputs =
    new Inputs(
      compilers,
      new Options(classpath, sources, classesDirectory, options, javacOptions, maxErrors, foldMappers(sourcePositionMappers), order),
      incSetup,
      previousAnalysis
    )

  def compilers(cpOptions: ClasspathOptions)(implicit app: AppConfiguration, log: Logger): Compilers =
    {
      val scalaProvider = app.provider.scalaProvider
      compilers(ScalaInstance(scalaProvider.version, scalaProvider.launcher), cpOptions)
    }

  def compilers(instance: ScalaInstance, cpOptions: ClasspathOptions)(implicit app: AppConfiguration, log: Logger): Compilers =
    compilers(instance, cpOptions, None)

  def compilers(instance: ScalaInstance, cpOptions: ClasspathOptions, javaHome: Option[File])(implicit app: AppConfiguration, log: Logger): Compilers =
    {
      val javac = AggressiveCompile.directOrFork(instance, cpOptions, javaHome)
      compilers(instance, cpOptions, javac)
    }
  def compilers(instance: ScalaInstance, cpOptions: ClasspathOptions, javac: JavaCompiler.Fork)(implicit app: AppConfiguration, log: Logger): Compilers =
    {
      val javaCompiler = JavaCompiler.fork(cpOptions, instance)(javac)
      compilers(instance, cpOptions, javaCompiler)
    }
  def compilers(instance: ScalaInstance, cpOptions: ClasspathOptions, javac: JavaTool)(implicit app: AppConfiguration, log: Logger): Compilers =
    {
      val scalac = scalaCompiler(instance, cpOptions)
      new Compilers(scalac, javac)
    }
  def scalaCompiler(instance: ScalaInstance, cpOptions: ClasspathOptions)(implicit app: AppConfiguration, log: Logger): AnalyzingCompiler =
    {
      val launcher = app.provider.scalaProvider.launcher
      val componentManager = new ComponentManager(launcher.globalLock, app.provider.components, Option(launcher.ivyHome), log)
      val provider = ComponentCompiler.interfaceProvider(componentManager)
      new AnalyzingCompiler(instance, provider, cpOptions, log)
    }
  def apply(in: Inputs, log: Logger): AnalysisResult =
    {
      import in.compilers._
      import in.config._
      import in.incSetup._
      apply(in, log, new LoggerReporter(maxErrors, log, sourcePositionMapper))
    }
  def apply(in: Inputs, log: Logger, reporter: xsbti.Reporter): AnalysisResult =
    {
      import in.compilers._
      import in.config._
      import in.incSetup._
      val agg = new AggressiveCompile
      AnalysisResult.tupled(agg(scalac, javac, sources, classpath, CompileOutput(classesDirectory), cache, None, options, javacOptions,
        in.previousAnalysis.analysis, in.previousAnalysis.setup, analysisMap, definesClass, reporter, order, skip,
        incOptions)(log))
    }

  private[sbt] def foldMappers[A](mappers: Seq[A => Option[A]]) =
    mappers.foldRight({ p: A => p }) { (mapper, mappers) => { p: A => mapper(p).getOrElse(mappers(p)) } }
}
