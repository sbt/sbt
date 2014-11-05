package sbt.compiler

import java.io.File
import java.lang.ref.{ SoftReference, Reference }

import sbt.classfile.Analyze
import sbt.classpath.ClasspathUtilities
import sbt.compiler.javac.AnalyzingJavaCompiler
import sbt.inc.Locate.DefinesClass
import sbt._
import sbt.inc._
import sbt.inc.Locate
import xsbti.{ AnalysisCallback, Reporter }
import xsbti.api.Source
import xsbti.compile.CompileOrder._
import xsbti.compile._

/** An instance of an analyzing compiler that can run both javac + scalac. */
final class MixedAnalyzingCompiler(
    val scalac: AnalyzingCompiler,
    val javac: AnalyzingJavaCompiler,
    val config: CompileConfiguration,
    val log: Logger) {
  import config._
  import currentSetup._

  private[this] val absClasspath = classpath.map(_.getAbsoluteFile)
  /** Mechanism to work with compiler arguments. */
  private[this] val cArgs = new CompilerArguments(compiler.scalaInstance, compiler.cp)

  /**
   * Compiles the given Java/Scala files.
   *
   * @param include  The files to compile right now
   * @param changes  A list of dependency changes.
   * @param callback  The callback where we report dependency issues.
   */
  def compile(include: Set[File], changes: DependencyChanges, callback: AnalysisCallback): Unit = {
    val outputDirs = outputDirectories(output)
    outputDirs foreach (IO.createDirectory)
    val incSrc = sources.filter(include)
    val (javaSrcs, scalaSrcs) = incSrc partition javaOnly
    logInputs(log, javaSrcs.size, scalaSrcs.size, outputDirs)
    /** compiles the scala code necessary using the analyzing compiler. */
    def compileScala(): Unit =
      if (!scalaSrcs.isEmpty) {
        val sources = if (order == Mixed) incSrc else scalaSrcs
        val arguments = cArgs(Nil, absClasspath, None, options.options)
        timed("Scala compilation", log) {
          compiler.compile(sources, changes, arguments, output, callback, reporter, config.cache, log, progress)
        }
      }
    /**
     * Compiles the Java code necessary.  All analysis code is included in this method.
     */
    def compileJava(): Unit =
      if (!javaSrcs.isEmpty) {
        // Runs the analysis portion of Javac.
        timed("Java compile + analysis", log) {
          javac.compile(javaSrcs, options.javacOptions.toArray[String], output, callback, reporter, log, progress)
        }
      }
    // TODO - Maybe on "Mixed" we should try to compile both Scala + Java.
    if (order == JavaThenScala) { compileJava(); compileScala() } else { compileScala(); compileJava() }
  }

  private[this] def outputDirectories(output: Output): Seq[File] = output match {
    case single: SingleOutput => List(single.outputDirectory)
    case mult: MultipleOutput => mult.outputGroups map (_.outputDirectory)
  }
  /** Debugging method to time how long it takes to run various compilation tasks. */
  private[this] def timed[T](label: String, log: Logger)(t: => T): T = {
    val start = System.nanoTime
    val result = t
    val elapsed = System.nanoTime - start
    log.debug(label + " took " + (elapsed / 1e9) + " s")
    result
  }

  private[this] def logInputs(log: Logger, javaCount: Int, scalaCount: Int, outputDirs: Seq[File]) {
    val scalaMsg = Analysis.counted("Scala source", "", "s", scalaCount)
    val javaMsg = Analysis.counted("Java source", "", "s", javaCount)
    val combined = scalaMsg ++ javaMsg
    if (!combined.isEmpty)
      log.info(combined.mkString("Compiling ", " and ", " to " + outputDirs.map(_.getAbsolutePath).mkString(",") + "..."))
  }

  /** Returns true if the file is java. */
  private[this] def javaOnly(f: File) = f.getName.endsWith(".java")
}

/**
 * This is a compiler that mixes the `sbt.compiler.AnalyzingCompiler` for Scala incremental compilation
 * with a `xsbti.JavaCompiler`, allowing cross-compilation of mixed Java/Scala projects with analysis output.
 *
 *
 * NOTE: this class *defines* how to run one step of cross-Java-Scala compilation and then delegates
 *       down to the incremental compiler for the rest.
 */
object MixedAnalyzingCompiler {
  /** The result of running the compilation. */
  final case class Result(analysis: Analysis, setup: CompileSetup, hasModified: Boolean)

  /**
   * This will run a mixed-compilation of Java/Scala sources
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
  def analyzingCompile(scalac: AnalyzingCompiler,
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
    analysisMap: File => Option[Analysis] = { _ => None },
    definesClass: DefinesClass = Locate.definesClass _,
    reporter: Reporter,
    compileOrder: CompileOrder = Mixed,
    skip: Boolean = false,
    incrementalCompilerOptions: IncOptions)(implicit log: Logger): Result =
    {
      val setup = new CompileSetup(output, new CompileOptions(options, javacOptions),
        scalac.scalaInstance.actualVersion, compileOrder, incrementalCompilerOptions.nameHashing)
      val (analysis, modified) = compile1(sources, classpath, setup, progress, previousAnalysis, previousSetup, analysisMap, definesClass,
        scalac, javac, reporter, skip, cache, incrementalCompilerOptions)
      Result(analysis, setup, modified)
    }

  def withBootclasspath(args: CompilerArguments, classpath: Seq[File]): Seq[File] =
    args.bootClasspathFor(classpath) ++ args.extClasspath ++ args.finishClasspath(classpath)

  /**
   * The first level of compilation.  This checks to see if we need to skip compiling because of the skip flag,
   * IF we're not skipping, this setups up the `CompileConfiguration` and delegates to `compile2`
   */
  private def compile1(sources: Seq[File],
    classpath: Seq[File],
    setup: CompileSetup, progress: Option[CompileProgress],
    previousAnalysis: Analysis,
    previousSetup: Option[CompileSetup],
    analysis: File => Option[Analysis],
    definesClass: DefinesClass,
    compiler: AnalyzingCompiler,
    javac: xsbti.compile.JavaCompiler,
    reporter: Reporter, skip: Boolean,
    cache: GlobalsCache,
    incrementalCompilerOptions: IncOptions)(implicit log: Logger): (Analysis, Boolean) =
    {
      import CompileSetup._
      if (skip)
        (previousAnalysis, false)
      else {
        val config = new CompileConfiguration(sources, classpath, previousAnalysis, previousSetup, setup,
          progress, analysis, definesClass, reporter, compiler, javac, cache, incrementalCompilerOptions)
        val (modified, result) = compile2(config)
        (result, modified)
      }
    }
  /**
   * This runs the actual compilation of Java + Scala.  There are two helper methods which
   * actually perform the compilation of Java or Scala.   This method uses the compile order to determine
   * which files to pass to which compilers.
   */
  private def compile2(config: CompileConfiguration)(implicit log: Logger, equiv: Equiv[CompileSetup]): (Boolean, Analysis) =
    {
      import config._
      import currentSetup._
      // TODO - most of this around classpath-ness sohuld move to AnalyzingJavaCompiler constructor
      val absClasspath = classpath.map(_.getAbsoluteFile)
      val apiOption = (api: Either[Boolean, Source]) => api.right.toOption
      val cArgs = new CompilerArguments(compiler.scalaInstance, compiler.cp)
      val searchClasspath = explicitBootClasspath(options.options) ++ withBootclasspath(cArgs, absClasspath)
      val entry = Locate.entry(searchClasspath, definesClass)
      // Construct a compiler which can handle both java and scala sources.
      val mixedCompiler =
        new MixedAnalyzingCompiler(
          compiler,
          // TODO - Construction of analyzing Java compiler MAYBE should be earlier...
          new AnalyzingJavaCompiler(javac, classpath, compiler.scalaInstance, entry, searchClasspath),
          config,
          log
        )

      // Here we check to see if any previous compilation results are invalid, based on altered
      // javac/scalac options.
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
      IncrementalCompile(sourcesSet, entry, mixedCompiler.compile, analysis, getAnalysis, output, log, incOptions)
    }

  /** Returns true if the file is java. */
  def javaOnly(f: File) = f.getName.endsWith(".java")

  private[this] def explicitBootClasspath(options: Seq[String]): Seq[File] =
    options.dropWhile(_ != CompilerArguments.BootClasspathOption).drop(1).take(1).headOption.toList.flatMap(IO.parseClasspath)

  private[this] val cache = new collection.mutable.HashMap[File, Reference[AnalysisStore]]
  private def staticCache(file: File, backing: => AnalysisStore): AnalysisStore =
    synchronized {
      cache get file flatMap { ref => Option(ref.get) } getOrElse {
        val b = backing
        cache.put(file, new SoftReference(b))
        b
      }
    }

  /** Create a an analysis store cache at the desired location. */
  def staticCachedStore(cacheFile: File) = staticCache(cacheFile, AnalysisStore.sync(AnalysisStore.cached(FileBasedStore(cacheFile))))

}
