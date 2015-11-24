package sbt
package compiler

import java.io.File

import sbt.inc.Analysis
import sbt.inc.IncOptions
import xsbti.Logger
import xsbti.Maybe
import xsbti.Reporter
import xsbti.compile.CompileProgress
import xsbti.compile.Compilers
import xsbti.compile.GlobalsCache
import xsbti.compile.Options

class CachingCompiler private (cacheFile: File) {
  import IC._
  /**
   * Inspired by `IC.compile` and `AggressiveCompile.compile1`
   *
   *  We need to duplicate `IC.compile`, because Java interface passes the incremental
   *  compiler options `IncOptions` as `Map[String, String]`, which is not expressive
   *  enough to use the transactional classfile manager (required for correctness).
   *  In other terms, we need richer (`IncOptions`) parameter type, here.
   *  Other thing is the update of the `AnalysisStore` implemented in `AggressiveCompile.compile1`
   *  method which is not implemented in `IC.compile`.
   */
  def compile(in: SbtInputs, comps: Compilers[AnalyzingCompiler])(logger: Logger): Analysis = {
    val options = in.options; import options.{ options => scalacOptions, _ }
    val aMap = (f: File) => m2o(in.analysisMap(f))
    val (previousAnalysis, previousSetup) = previousAnalysisFromStore(cacheFile, in.incOptions.nameHashing)
    import comps._
    cacheAndReturnLastAnalysis(IC.incrementalCompile(scalac, javac, options.sources, classpath, output, in.cache,
      m2o(in.progress), scalacOptions, javacOptions, previousAnalysis, previousSetup, aMap, in.definesClass,
      in.reporter, order, skip = false, in.incOptions)(logger))
  }

  private def cacheAndReturnLastAnalysis(compilationResult: IC.Result): Analysis = {
    if (compilationResult.hasModified)
      MixedAnalyzingCompiler.staticCachedStore(cacheFile).set(compilationResult.analysis, compilationResult.setup)
    compilationResult.analysis
  }
}

object CachingCompiler {
  def apply(cacheFile: File): CachingCompiler =
    new CachingCompiler(cacheFile)
}

trait SbtInputs {
  def incOptions: IncOptions
  def options: Options
  def analysisMap(f: File): Maybe[Analysis]
  def cache: GlobalsCache
  def progress: Maybe[CompileProgress]
  def reporter: Reporter

  /**
   * Provides a function to determine if classpath entry `file` contains a given class.
   *  The returned function should generally cache information about `file`, such as the list of entries in a jar.
   */
  def definesClass(file: File): String => Boolean
}
