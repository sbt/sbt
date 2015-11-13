package sbt
package internal
package inc

import java.io.File
import sbt.internal.inc.javac.AnalyzingJavaCompiler
import Locate._
import xsbti.Logger
import xsbti.api.Source
import xsbti.compile.CompileOrder._
import xsbti.compile.DefinesClass
import xsbti.{ Reporter, Logger, Maybe }
import xsbti.compile._
import sbt.io.IO
import sbt.internal.io.Using

// TODO -
//  1. Move analyzingCompile from MixedAnalyzingCompiler into here
//  2. Create AnalyzingJavaComiler class
//  3. MixedAnalyzingCompiler should just provide the raw 'compile' method used in incremental compiler (and
//     by this class.

/**
 * An implementation of the incremental compiler that can compile inputs and dump out source dependency analysis.
 */
private[sbt] object IC {

  /**
   * This will run a mixed-compilation of Java/Scala sources
   *
   *
   * TODO - this is the interface sbt uses. Somehow this needs to be exposed further.
   *
   * @param scalac  An instances of the Scalac compiler which can also extract "Analysis" (dependencies)
   * @param javac  An instance of the Javac compiler.
   * @param sources  The set of sources to compile
   * @param classpath  The classpath to use when compiling.
   * @param output  Configuration for where to output .class files.
   * @param cache   The caching mechanism to use instead of insantiating new compiler instances.
   * @param progress  Progress listening for the compilation process.  TODO - Feed this through the Javac Compiler!
   * @param options   Options for the Scala compiler
   * @param javacOptions  Options for the Java compiler
   * @param previousAnalysis  The previous dependency Analysis object/
   * @param previousSetup  The previous compilation setup (if any)
   * @param analysisMap   A map of file to the dependency analysis of that file.
   * @param definesClass  A mehcnaism of looking up whether or not a JAR defines a particular Class.
   * @param reporter  Where we sent all compilation error/warning events
   * @param compileOrder  The order we'd like to mix compilation.  JavaThenScala, ScalaThenJava or Mixed.
   * @param skip  IF true, we skip compilation and just return the previous analysis file.
   * @param incrementalCompilerOptions  Options specific to incremental compilation.
   * @param log  The location where we write log messages.
   * @return  The full configuration used to instantiate this mixed-analyzing compiler, the set of extracted dependencies and
   *          whether or not any file were modified.
   */
  def incrementalCompile(
    scalac: AnalyzingCompiler,
    javac: xsbti.compile.JavaCompiler,
    sources: Seq[File],
    classpath: Seq[File],
    output: Output,
    cache: GlobalsCache,
    progress: Option[CompileProgress] = None,
    options: Seq[String] = Nil,
    javacOptions: Seq[String] = Nil,
    previousAnalysis: Option[CompileAnalysis],
    previousSetup: Option[MiniSetup],
    analysisMap: File => Option[CompileAnalysis] = { _ => None },
    definesClass: Locate.DefinesClass = Locate.definesClass _,
    reporter: Reporter,
    compileOrder: CompileOrder = Mixed,
    skip: Boolean = false,
    incrementalCompilerOptions: IncOptions
  )(implicit log: Logger): CompileResult = {
    val prev = previousAnalysis match {
      case Some(previous) => previous
      case None           => Analysis.empty(incrementalCompilerOptions.nameHashing)
    }
    val config = MixedAnalyzingCompiler.makeConfig(scalac, javac, sources, classpath, output, cache,
      progress, options, javacOptions, prev, previousSetup, analysisMap, definesClass, reporter,
      compileOrder, skip, incrementalCompilerOptions)
    import config.{ currentSetup => setup }

    if (skip) new CompileResult(prev, setup, false)
    else {
      val (analysis, changed) = compileInternal(MixedAnalyzingCompiler(config)(log))
      new CompileResult(analysis, setup, changed)
    }
  }

  /** Actually runs the incremental compiler using the given mixed compiler.  This will prune the inputs based on the MiniSetup. */
  private def compileInternal(mixedCompiler: MixedAnalyzingCompiler)(implicit log: Logger, equiv: Equiv[MiniSetup]): (Analysis, Boolean) = {
    val entry = MixedAnalyzingCompiler.classPathLookup(mixedCompiler.config)
    import mixedCompiler.config._
    import mixedCompiler.config.currentSetup.output
    val sourcesSet = sources.toSet
    val analysis = previousSetup match {
      case Some(previous) if previous.nameHashing != currentSetup.nameHashing =>
        // if the value of `nameHashing` flag has changed we have to throw away
        // previous Analysis completely and start with empty Analysis object
        // that supports the particular value of the `nameHashing` flag.
        // Otherwise we'll be getting UnsupportedOperationExceptions
        Analysis.empty(currentSetup.nameHashing)
      case Some(previous) if equiv.equiv(previous, currentSetup) => previousAnalysis
      case _ => Incremental.prune(sourcesSet, previousAnalysis)
    }
    // Run the incremental compiler using the mixed compiler we've defined.
    IncrementalCompile(sourcesSet, entry, mixedCompiler.compile, analysis, getAnalysis, output, log, incOptions).swap
  }
}
