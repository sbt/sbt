package sbt.compiler

import java.io.File
import sbt.compiler.javac.AnalyzingJavaCompiler
import sbt.inc.Locate._
import sbt._
import sbt.inc._
import xsbti.Logger
import xsbti.api.Source
import xsbti.compile.ClasspathOptions
import xsbti.compile.CompileOrder._
import xsbti.compile.DefinesClass
import xsbti.compile.ScalaInstance
import xsbti.{ Logger, Maybe, Reporter }
import xsbti.compile._

// TODO -
//  1. Move analyzingCompile from MixedAnalyzingCompiler into here
//  2. Create AnalyzingJavaComiler class
//  3. MixedAnalyzingCompiler should just provide the raw 'compile' method used in incremental compiler (and
//     by this class.

/**
  * An implementation of the incremental compiler that can compile inputs and dump out source dependency analysis.
  */
object IC extends IncrementalCompiler[Analysis, AnalyzingCompiler] {

  override def compile(in: Inputs[Analysis, AnalyzingCompiler], log: Logger): Analysis = {
    val setup = in.setup; import setup._
    val options = in.options; import options.{ options => scalacOptions, _ }
    val compilers = in.compilers; import compilers._
    val aMap = (f: File) => m2o(analysisMap(f))
    val defClass = (f: File) => {
      val dc = definesClass(f);
      (name: String) =>
        dc.apply(name)
    }
    val incOptions = IncOptions.fromStringMap(incrementalCompilerOptions)
    val (previousAnalysis, previousSetup) = {
      MixedAnalyzingCompiler.staticCachedStore(setup.cacheFile()).get().map {
        case (a, s) => (a, Some(s))
      } getOrElse {
        (Analysis.empty(nameHashing = incOptions.nameHashing), None)
      }
    }
    incrementalCompile(
      scalac,
      javac,
      sources,
      classpath,
      output,
      cache,
      m2o(progress),
      scalacOptions,
      javacOptions,
      previousAnalysis,
      previousSetup,
      aMap,
      defClass,
      reporter,
      order,
      skip,
      incOptions
    )(log).analysis
  }

  private[this] def m2o[S](opt: Maybe[S]): Option[S] = if (opt.isEmpty) None else Some(opt.get)

  @deprecated("A logger is no longer needed.", "0.13.8")
  override def newScalaCompiler(instance: ScalaInstance,
                                interfaceJar: File,
                                options: ClasspathOptions,
                                log: Logger): AnalyzingCompiler =
    new AnalyzingCompiler(instance, CompilerInterfaceProvider.constant(interfaceJar), options)

  override def newScalaCompiler(instance: ScalaInstance,
                                interfaceJar: File,
                                options: ClasspathOptions): AnalyzingCompiler =
    new AnalyzingCompiler(instance, CompilerInterfaceProvider.constant(interfaceJar), options)

  def compileInterfaceJar(label: String,
                          sourceJar: File,
                          targetJar: File,
                          interfaceJar: File,
                          instance: ScalaInstance,
                          log: Logger) {
    val raw = new RawCompiler(instance, sbt.ClasspathOptions.auto, log)
    AnalyzingCompiler.compileSources(sourceJar :: Nil, targetJar, interfaceJar :: Nil, label, raw, log)
  }

  def readCache(file: File): Maybe[(Analysis, CompileSetup)] =
    try { Maybe.just(readCacheUncaught(file)) } catch { case _: Exception => Maybe.nothing() }

  @deprecated("Use overloaded variant which takes `IncOptions` as parameter.", "0.13.2")
  def readAnalysis(file: File): Analysis =
    try { readCacheUncaught(file)._1 } catch { case _: Exception => Analysis.Empty }

  def readAnalysis(file: File, incOptions: IncOptions): Analysis =
    try { readCacheUncaught(file)._1 } catch {
      case _: Exception => Analysis.empty(nameHashing = incOptions.nameHashing)
    }

  def readCacheUncaught(file: File): (Analysis, CompileSetup) =
    Using.fileReader(IO.utf8)(file) { reader =>
      try {
        TextAnalysisFormat.read(reader)
      } catch {
        case ex: sbt.inc.ReadException =>
          throw new java.io.IOException(s"Error while reading $file", ex)
      }
    }

  /** The result of running the compilation. */
  final case class Result(analysis: Analysis, setup: CompileSetup, hasModified: Boolean)

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
  def incrementalCompile(scalac: AnalyzingCompiler,
                         javac: xsbti.compile.JavaCompiler,
                         sources: Seq[File],
                         classpath: Seq[File],
                         output: Output,
                         cache: GlobalsCache,
                         progress: Option[CompileProgress] = None,
                         options: Seq[String] = Nil,
                         javacOptions: Seq[String] = Nil,
                         previousAnalysis: Analysis,
                         previousSetup: Option[CompileSetup],
                         analysisMap: File => Option[Analysis] = { _ =>
                           None
                         },
                         definesClass: Locate.DefinesClass = Locate.definesClass _,
                         reporter: Reporter,
                         compileOrder: CompileOrder = Mixed,
                         skip: Boolean = false,
                         incrementalCompilerOptions: IncOptions)(implicit log: Logger): Result = {
    val config = MixedAnalyzingCompiler.makeConfig(
      scalac,
      javac,
      sources,
      classpath,
      output,
      cache,
      progress,
      options,
      javacOptions,
      previousAnalysis,
      previousSetup,
      analysisMap,
      definesClass,
      reporter,
      compileOrder,
      skip,
      incrementalCompilerOptions
    )
    import config.{ currentSetup => setup }

    if (skip) Result(previousAnalysis, setup, false)
    else {
      val (analysis, changed) = compileInternal(MixedAnalyzingCompiler(config)(log))
      Result(analysis, setup, changed)
    }
  }

  /** Actually runs the incremental compiler using the given mixed compiler.  This will prune the inputs based on the CompileSetup. */
  private def compileInternal(
      mixedCompiler: MixedAnalyzingCompiler
  )(implicit log: Logger, equiv: Equiv[CompileSetup]): (Analysis, Boolean) = {
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
      case _                                                     => Incremental.prune(sourcesSet, previousAnalysis)
    }
    // Run the incremental compiler using the mixed compiler we've defined.
    IncrementalCompile(
      sourcesSet,
      entry,
      mixedCompiler.compile,
      analysis,
      getAnalysis,
      output,
      log,
      incOptions
    ).swap
  }
}
