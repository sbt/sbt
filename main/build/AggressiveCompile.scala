/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package build

import inc._

	import java.io.File
	import compile.{AnalyzingCompiler, CompilerArguments, JavaCompiler}
	import classpath.ClasspathUtilities
	import classfile.Analyze
	import xsbti.api.Source
	import xsbti.AnalysisCallback
	import CompileSetup._
	import sbinary.DefaultProtocol.{ immutableMapFormat, immutableSetFormat, StringFormat }

final class CompileConfiguration(val sources: Seq[File], val classpath: Seq[File], val javaSrcBases: Seq[File],
	val previousAnalysis: Analysis, val previousSetup: Option[CompileSetup], val currentSetup: CompileSetup, val getAnalysis: File => Option[Analysis],
	val maxErrors: Int, val compiler: AnalyzingCompiler, val javac: JavaCompiler)

class AggressiveCompile(cacheDirectory: File)
{
	def apply(compiler: AnalyzingCompiler, javac: JavaCompiler, sources: Seq[File], classpath: Seq[File], outputDirectory: File, javaSrcBases: Seq[File] = Nil, options: Seq[String] = Nil, javacOptions: Seq[String] = Nil, analysisMap: Map[File, Analysis] = Map.empty, maxErrors: Int = 100)(implicit log: Logger): Analysis =
	{
		val setup = new CompileSetup(outputDirectory, new CompileOptions(options, javacOptions), compiler.scalaInstance.actualVersion, CompileOrder.Mixed)
		compile1(sources, classpath, javaSrcBases, setup, store, analysisMap, compiler, javac, maxErrors)
	}

	def withBootclasspath(args: CompilerArguments, classpath: Seq[File]): Seq[File] =
		args.bootClasspath ++ args.finishClasspath(classpath)

	def compile1(sources: Seq[File], classpath: Seq[File], javaSrcBases: Seq[File], setup: CompileSetup, store: AnalysisStore, analysis: Map[File, Analysis], compiler: AnalyzingCompiler, javac: JavaCompiler, maxErrors: Int)(implicit log: Logger): Analysis =
	{
		val (previousAnalysis, previousSetup) = extract(store.get())
		val config = new CompileConfiguration(sources, classpath, javaSrcBases, previousAnalysis, previousSetup, setup, analysis.get _, maxErrors, compiler, javac)
		val result = compile2(config)
		store.set(result, setup)
		result
	}
	def compile2(config: CompileConfiguration)(implicit log: Logger, equiv: Equiv[CompileSetup]): Analysis =
	{
		import config._
		import currentSetup._
		val getAPI = (f: File) => {
			val extApis = getAnalysis(f) match { case Some(a) => a.apis.external; case None => Map.empty[String, Source] }
			extApis.get _
		}
		val apiOption= (api: Either[Boolean, Source]) => api.right.toOption
		val cArgs = new CompilerArguments(compiler.scalaInstance, compiler.cp)
		val searchClasspath = withBootclasspath(cArgs, classpath)
		val entry = Locate.entry(searchClasspath)
		
		val compile0 = (include: Set[File], callback: AnalysisCallback) => {
			IO.createDirectory(outputDirectory)
			val incSrc = sources.filter(include)
			println("Compiling:\n\t" + incSrc.mkString("\n\t"))
			val arguments = cArgs(incSrc, classpath, outputDirectory, options.options)
			compiler.compile(arguments, callback, maxErrors, log)
			val javaSrcs = incSrc.filter(javaOnly) 
			if(!javaSrcs.isEmpty)
			{
				import Path._
				val loader = ClasspathUtilities.toLoader(classpath, compiler.scalaInstance.loader)
				// TODO: Analyze needs to generate API from Java class files
				def readAPI(source: File, classes: Seq[Class[_]]) { callback.api(source, ClassToAPI(classes)) }
				Analyze(outputDirectory, javaSrcs, javaSrcBases, log)(callback, loader, readAPI) {
					javac(javaSrcs, classpath, outputDirectory, options.javacOptions)
				}
			}
		}
		
		val sourcesSet = sources.toSet
		val analysis = previousSetup match {
			case Some(previous) if equiv.equiv(previous, currentSetup) => previousAnalysis
			case _ => Incremental.prune(sourcesSet, previousAnalysis)
		}
		IncrementalCompile(sourcesSet, entry, compile0, analysis, getAnalysis, outputDirectory)
	}
	private def extract(previous: Option[(Analysis, CompileSetup)]): (Analysis, Option[CompileSetup]) =
		previous match
		{
			case Some((an, setup)) => (an, Some(setup))
			case None => (Analysis.Empty, None)
		}
	def javaOnly(f: File) = f.getName.endsWith(".java")

	import AnalysisFormats._
	val store = AnalysisStore.sync(AnalysisStore.cached(FileBasedStore(cacheDirectory)))
}