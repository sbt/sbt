package sbt
package internal
package inc

import sbt.internal.inc.javac.{ IncrementalCompilerJavaTools, JavaTools }
import xsbti.{ Position, Logger, Maybe, Reporter }
import xsbti.compile.{ CompileOrder, GlobalsCache, IncOptions, MiniSetup, CompileAnalysis, CompileResult, CompileOptions }
import xsbti.compile.{ PreviousResult, Setup, Inputs, IncrementalCompiler, F1, DefinesClass }
import xsbti.compile.{ Compilers => XCompilers }
import java.io.File
import sbt.util.Logger.m2o

class IncrementalCompilerImpl extends IncrementalCompiler {
  import IncrementalCompilerImpl._

  override def compile(in: Inputs, log: Logger): CompileResult =
    {
      val cs = in.compilers()
      val config = in.options()
      val setup = in.setup()
      import cs._
      import config._
      import setup._
      // Here is some trickery to choose the more recent (reporter-using) java compiler rather
      // than the previously defined versions.
      // TODO - Remove this hackery in sbt 1.0.
      val javacChosen: xsbti.compile.JavaCompiler =
        in.compilers match {
          case Compilers(_, javac) => javac.xsbtiCompiler
        } // ).getOrElse(in.inputs.compilers.javac)
      val scalac = in.compilers match {
        case Compilers(scalac, _) => scalac
      }
      IC.incrementalCompile(scalac, javacChosen, sources, classpath, CompileOutput(classesDirectory), cache, None, scalacOptions, javacOptions,
        m2o(in.previousResult.analysis),
        m2o(in.previousResult.setup),
        { f => m2o(analysisMap()(f)) },
        { f => { s => definesClass()(f)(s) } },
        reporter, order, skip, incrementalCompilerOptions)(log)
    }
  def setup(analysisMap: F1[File, Maybe[CompileAnalysis]], definesClass: F1[File, DefinesClass], skip: Boolean, cacheFile: File, cache: GlobalsCache,
    incrementalCompilerOptions: IncOptions, reporter: Reporter): Setup =
    new Setup(analysisMap, definesClass, skip, cacheFile, cache, incrementalCompilerOptions, reporter)
  def inputs(options: CompileOptions, compilers: XCompilers, setup: Setup, previousResult: PreviousResult): Inputs =
    new Inputs(compilers, options, setup, previousResult)
  def inputs(classpath: Array[File], sources: Array[File], classesDirectory: File, scalacOptions: Array[String],
    javacOptions: Array[String], maxErrors: Int, sourcePositionMappers: Array[F1[Position, Maybe[Position]]],
    order: CompileOrder,
    compilers: XCompilers, setup: Setup, previousResult: PreviousResult): Inputs =
    inputs(
      new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors,
        foldMappers(sourcePositionMappers), order),
      compilers, setup, previousResult
    )
  def emptyPreviousResult: PreviousResult = new PreviousResult(Maybe.nothing[CompileAnalysis], Maybe.nothing[MiniSetup])
  private[sbt] def f1[A](f: A => A): F1[A, A] =
    new F1[A, A] {
      def apply(a: A): A = f(a)
    }
  private[sbt] def foldMappers[A](mappers: Array[F1[A, Maybe[A]]]) =
    mappers.foldRight(f1[A](identity)) { (mapper, mappers) =>
      f1[A]({ p: A =>
        m2o(mapper(p)).getOrElse(mappers(p))
      })
    }
  def compilers(instance: ScalaInstance, cpOptions: ClasspathOptions, javaHome: Option[File],
    scalac: AnalyzingCompiler): XCompilers =
    {
      val javac = JavaTools.directOrFork(instance, cpOptions, javaHome)
      Compilers(scalac, javac)
    }
  def compilers(javac: IncrementalCompilerJavaTools, scalac: AnalyzingCompiler): XCompilers =
    Compilers(scalac, javac)
}

private[sbt] object IncrementalCompilerImpl {
  /** The instances of Scalac/Javac used to compile the current project. */
  final case class Compilers(scalac: AnalyzingCompiler, javac: IncrementalCompilerJavaTools) extends XCompilers
}
