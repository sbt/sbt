/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import inc._

	import java.io.File
	import compiler.{AnalyzingCompiler, CompilerArguments, JavaCompiler}
	import classpath.ClasspathUtilities
	import classfile.Analyze
	import xsbti.api.Source
	import xsbti.AnalysisCallback
	import inc.Locate.DefinesClass
	import CompileSetup._
	import CompileOrder.{JavaThenScala, Mixed, ScalaThenJava}
	import sbinary.DefaultProtocol.{ immutableMapFormat, immutableSetFormat, StringFormat }

final class CompileConfiguration(val sources: Seq[File], val classpath: Seq[File],
	val previousAnalysis: Analysis, val previousSetup: Option[CompileSetup], val currentSetup: CompileSetup, val getAnalysis: File => Option[Analysis], val definesClass: DefinesClass,
	val maxErrors: Int, val compiler: AnalyzingCompiler, val javac: JavaCompiler)

class AggressiveCompile(cacheDirectory: File)
{
	def apply(compiler: AnalyzingCompiler, javac: JavaCompiler, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String] = Nil, javacOptions: Seq[String] = Nil, analysisMap: Map[File, Analysis] = Map.empty, definesClass: DefinesClass = Locate.definesClass _, maxErrors: Int = 100, compileOrder: CompileOrder.Value = Mixed, skip: Boolean = false)(implicit log: Logger): Analysis =
	{
		val setup = new CompileSetup(outputDirectory, new CompileOptions(options, javacOptions), compiler.scalaInstance.actualVersion, compileOrder)
		compile1(sources, classpath, setup, store, analysisMap, definesClass, compiler, javac, maxErrors, skip)
	}

	def withBootclasspath(args: CompilerArguments, classpath: Seq[File]): Seq[File] =
		args.bootClasspath ++ args.finishClasspath(classpath)

	def compile1(sources: Seq[File], classpath: Seq[File], setup: CompileSetup, store: AnalysisStore, analysis: Map[File, Analysis], definesClass: DefinesClass, compiler: AnalyzingCompiler, javac: JavaCompiler, maxErrors: Int, skip: Boolean)(implicit log: Logger): Analysis =
	{
		val (previousAnalysis, previousSetup) = extract(store.get())
		if(skip)
			previousAnalysis
		else {
			val config = new CompileConfiguration(sources, classpath, previousAnalysis, previousSetup, setup, analysis.get _, definesClass, maxErrors, compiler, javac)
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
		
		val compile0 = (include: Set[File], callback: AnalysisCallback) => {
			IO.createDirectory(outputDirectory)
			val incSrc = sources.filter(include)
			val (javaSrcs, scalaSrcs) = incSrc partition javaOnly
			logInputs(log, javaSrcs.size, scalaSrcs.size, outputDirectory)
			def compileScala() =
				if(!scalaSrcs.isEmpty)
				{
					val sources = if(order == Mixed) incSrc else scalaSrcs
					val arguments = cArgs(sources, absClasspath, outputDirectory, options.options)
					compiler.compile(arguments, callback, maxErrors, log)
				}
			def compileJava() =
				if(!javaSrcs.isEmpty)
				{
					import Path._
					val loader = ClasspathUtilities.toLoader(searchClasspath)
					def readAPI(source: File, classes: Seq[Class[_]]) { callback.api(source, ClassToAPI(classes)) }
					Analyze(outputDirectory, javaSrcs, log)(callback, loader, readAPI) {
						javac(javaSrcs, absClasspath, outputDirectory, options.javacOptions)
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
	val store = AggressiveCompile.staticCache(cacheDirectory, AnalysisStore.sync(AnalysisStore.cached(FileBasedStore(cacheDirectory))))
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

	def evictCache(file: File): Unit = synchronized {
	  if(file.isDirectory) {
	    IO.listFiles(file).foreach(evictCache)
		}

	  cache.remove(file)
	}
}
