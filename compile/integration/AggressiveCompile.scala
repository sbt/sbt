/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package compiler

import inc._

	import java.io.File
	import classpath.ClasspathUtilities
	import classfile.Analyze
	import inc.Locate.DefinesClass
	import CompileSetup._
	import sbinary.DefaultProtocol.{ immutableMapFormat, immutableSetFormat, StringFormat }
	import Types.const

	import xsbti.AnalysisCallback
	import xsbti.api.Source
	import xsbti.compile.{CompileOrder, DependencyChanges, GlobalsCache}
	import CompileOrder.{JavaThenScala, Mixed, ScalaThenJava}

final class CompileConfiguration(val sources: Seq[File], val classpath: Seq[File],
	val previousAnalysis: Analysis, val previousSetup: Option[CompileSetup], val currentSetup: CompileSetup, val getAnalysis: File => Option[Analysis], val definesClass: DefinesClass,
	val maxErrors: Int, val compiler: AnalyzingCompiler, val javac: xsbti.compile.JavaCompiler, val cache: GlobalsCache)

class AggressiveCompile(cacheFile: File)
{
	def apply(compiler: AnalyzingCompiler, javac: xsbti.compile.JavaCompiler, sources: Seq[File], classpath: Seq[File], outputDirectory: File, cache: GlobalsCache, options: Seq[String] = Nil, javacOptions: Seq[String] = Nil, analysisMap: File => Option[Analysis] = const(None), definesClass: DefinesClass = Locate.definesClass _, maxErrors: Int = 100, compileOrder: CompileOrder = Mixed, skip: Boolean = false)(implicit log: Logger): Analysis =
	{
		val setup = new CompileSetup(outputDirectory, new CompileOptions(options, javacOptions), compiler.scalaInstance.actualVersion, compileOrder)
		compile1(sources, classpath, setup, store, analysisMap, definesClass, compiler, javac, maxErrors, skip, cache)
	}

	def withBootclasspath(args: CompilerArguments, classpath: Seq[File]): Seq[File] =
		args.bootClasspath ++ args.finishClasspath(classpath)

	def compile1(sources: Seq[File], classpath: Seq[File], setup: CompileSetup, store: AnalysisStore, analysis: File => Option[Analysis], definesClass: DefinesClass, compiler: AnalyzingCompiler, javac: xsbti.compile.JavaCompiler, maxErrors: Int, skip: Boolean, cache: GlobalsCache)(implicit log: Logger): Analysis =
	{
		val (previousAnalysis, previousSetup) = extract(store.get())
		if(skip)
			previousAnalysis
		else {
			val config = new CompileConfiguration(sources, classpath, previousAnalysis, previousSetup, setup, analysis, definesClass, maxErrors, compiler, javac, cache)
			val (modified, result) = compile2(config)
			if(modified)
				store.set(result, setup)
			result
		}
	}
	def compile2(config: CompileConfiguration)(implicit log: Logger, equiv: Equiv[CompileSetup]): (Boolean, Analysis) =
	{
		import config._
		import currentSetup._
		val absClasspath = classpath.map(_.getCanonicalFile)
		val apiOption= (api: Either[Boolean, Source]) => api.right.toOption
		val cArgs = new CompilerArguments(compiler.scalaInstance, compiler.cp)
		val searchClasspath = explicitBootClasspath(options.options) ++ withBootclasspath(cArgs, absClasspath)
		val entry = Locate.entry(searchClasspath, definesClass)
		
		val compile0 = (include: Set[File], changes: DependencyChanges, callback: AnalysisCallback) => {
			IO.createDirectory(outputDirectory)
			val incSrc = sources.filter(include)
			val (javaSrcs, scalaSrcs) = incSrc partition javaOnly
			logInputs(log, javaSrcs.size, scalaSrcs.size, outputDirectory)
			def compileScala() =
				if(!scalaSrcs.isEmpty)
				{
					val sources = if(order == Mixed) incSrc else scalaSrcs
					val arguments = cArgs(Nil, absClasspath, outputDirectory, options.options)
					compiler.compile(sources, changes, arguments, callback, maxErrors, cache, log)
				}
			def compileJava() =
				if(!javaSrcs.isEmpty)
				{
					import Path._
					val loader = ClasspathUtilities.toLoader(searchClasspath)
					def readAPI(source: File, classes: Seq[Class[_]]) { callback.api(source, ClassToAPI(classes)) }
					Analyze(outputDirectory, javaSrcs, log)(callback, loader, readAPI) {
						javac.compile(javaSrcs.toArray, absClasspath.toArray, outputDirectory, options.javacOptions.toArray, maxErrors, log)
					}
				}
			if(order == JavaThenScala) { compileJava(); compileScala() } else { compileScala(); compileJava() }
		}
		
		val sourcesSet = sources.toSet
		val analysis = previousSetup match {
			case Some(previous) if equiv.equiv(previous, currentSetup) => previousAnalysis
			case _ => Incremental.prune(sourcesSet, previousAnalysis)
		}
		IncrementalCompile(sourcesSet, entry, compile0, analysis, getAnalysis, outputDirectory, log)
	}
	private[this] def logInputs(log: Logger, javaCount: Int, scalaCount: Int, out: File)
	{
		val scalaMsg = Util.counted("Scala source", "", "s", scalaCount)
		val javaMsg = Util.counted("Java source", "", "s", javaCount)
		val combined = scalaMsg ++ javaMsg
		if(!combined.isEmpty)
			log.info(combined.mkString("Compiling ", " and ", " to " + out.getAbsolutePath + "..."))
	}
	private def extract(previous: Option[(Analysis, CompileSetup)]): (Analysis, Option[CompileSetup]) =
		previous match
		{
			case Some((an, setup)) => (an, Some(setup))
			case None => (Analysis.Empty, None)
		}
	def javaOnly(f: File) = f.getName.endsWith(".java")

	private[this] def explicitBootClasspath(options: Seq[String]): Seq[File] =
		options.dropWhile(_ != CompilerArguments.BootClasspathOption).drop(1).take(1).headOption.toList.flatMap(IO.parseClasspath)

	import AnalysisFormats._
	val store = AggressiveCompile.staticCache(cacheFile, AnalysisStore.sync(AnalysisStore.cached(FileBasedStore(cacheFile))))
}
object AggressiveCompile
{
		import collection.mutable
		import java.lang.ref.{Reference,SoftReference}
	private[this] val cache = new collection.mutable.HashMap[File, Reference[AnalysisStore]]
	private def staticCache(file: File, backing: => AnalysisStore): AnalysisStore =
		synchronized {
			cache get file flatMap { ref => Option(ref.get) } getOrElse {
				val b = backing
				cache.put(file, new SoftReference(b))
				b
			}
		}

	def directOrFork(instance: ScalaInstance, cpOptions: ClasspathOptions, javaHome: Option[File]): JavaTool =
		if(javaHome.isDefined)
			JavaCompiler.fork(cpOptions, instance)(forkJavac(javaHome))
		else
			JavaCompiler.directOrFork(cpOptions, instance)(forkJavac(None))

	def forkJavac(javaHome: Option[File]): JavaCompiler.Fork =
	{
		import Path._
		def exec(jc: JavacContract) = javaHome match { case None => jc.name; case Some(jh) => (jh / "bin" / jc.name).absolutePath }
		(contract: JavacContract, args: Seq[String], log: Logger) => {
			log.debug("Forking " + contract.name + ": " + exec(contract) + " " + args.mkString(" "))
			val javacLogger = new JavacLogger(log)
			var exitCode = -1
			try {
				exitCode = Process(exec(contract), args) ! javacLogger
			} finally {
				javacLogger.flush(exitCode)
			}
			exitCode
		}
	}
}

private[sbt] class JavacLogger(log: Logger) extends ProcessLogger {
  import scala.collection.mutable.ListBuffer
  import Level.{Info, Warn, Error, Value => LogLevel}

  private val msgs: ListBuffer[(LogLevel, String)] = new ListBuffer()

  def info(s: => String): Unit =
    synchronized { msgs += ((Info, s)) }

  def error(s: => String): Unit =
    synchronized { msgs += ((Error, s)) }

  def buffer[T](f: => T): T = f

  private def print(desiredLevel: LogLevel)(t: (LogLevel, String)) = t match {
    case (Info, msg) => log.info(msg)
    case (Error, msg) => log.log(desiredLevel, msg)
  }

  def flush(exitCode: Int): Unit = {
    val level = if (exitCode == 0) Warn else Error
    msgs foreach print(level)
    msgs.clear()
  }
}
