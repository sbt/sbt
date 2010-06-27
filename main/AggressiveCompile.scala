/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import inc._

	import java.io.File
	import xsbt.{AnalyzingCompiler, CompileLogger, CompilerArguments}
	import xsbti.api.Source
	import xsbti.AnalysisCallback
	import CompileSetup._
	import sbinary.DefaultProtocol.{ immutableMapFormat, immutableSetFormat, StringFormat }

final class CompileConfiguration(val sources: Seq[File], val classpath: Seq[File], val previousAnalysis: Analysis,
	val previousSetup: CompileSetup, val currentSetup: CompileSetup, val getAnalysis: File => Option[Analysis],
	val maxErrors: Int, val compiler: AnalyzingCompiler)

class AggressiveCompile(cacheDirectory: File)
{
	def apply(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], compiler: AnalyzingCompiler, log: CompileLogger): Analysis =
	{
		val setup = new CompileSetup(outputDirectory, new CompileOptions(options), compiler.scalaInstance.actualVersion, CompileOrder.Mixed)
		compile1(sources, classpath, setup, store, Map.empty, compiler, log)
	}

	def withBootclasspath(args: CompilerArguments, classpath: Seq[File]): Seq[File] =
		args.bootClasspath ++ classpath

	def compile1(sources: Seq[File], classpath: Seq[File], setup: CompileSetup, store: AnalysisStore, analysis: Map[File, Analysis], compiler: AnalyzingCompiler, log: CompileLogger): Analysis =
	{
		val (previousAnalysis, previousSetup) = store.get()
		val config = new CompileConfiguration(sources, classpath, previousAnalysis, previousSetup, setup, analysis.get _, 100, compiler)
		val result = compile2(config, log)
		store.set(result, setup)
		result
	}
	def compile2(config: CompileConfiguration, log: CompileLogger)(implicit equiv: Equiv[CompileSetup]): Analysis =
	{
		import config._
		import currentSetup._
		val getAPI = (f: File) => {
			val extApis = getAnalysis(f) match { case Some(a) => a.apis.external; case None => Map.empty[String, Source] }
			extApis.get _
		}
		val apiOrEmpty = (api: Either[Boolean, Source]) => api.right.toOption.getOrElse( APIs.emptyAPI )
		val cArgs = new CompilerArguments(compiler.scalaInstance, compiler.cp)
		val externalAPI = apiOrEmpty compose Locate.value(withBootclasspath(cArgs, classpath), getAPI)
		val compile0 = (include: Set[File], callback: AnalysisCallback) => {
			val arguments = cArgs(sources.filter(include), classpath, outputDirectory, options.options)
			compiler.compile(arguments, callback, maxErrors, log)
		}
		val sourcesSet = sources.toSet
		val analysis = if(equiv.equiv(previousSetup, currentSetup)) previousAnalysis else Incremental.prune(sourcesSet, previousAnalysis)
		IncrementalCompile(sourcesSet, compile0, analysis, externalAPI)
	}

	import AnalysisFormats._
	// The following intermediate definitions are needed because of Scala's implicit parameter rules.
	//   implicit def a(implicit b: T[Int]): S = ...
	// triggers a divierging expansion because T[Int] dominates S, even though they are unrelated
	implicit val r = relationFormat[File,File]
	implicit val map = immutableMapFormat[File, Stamp]
	implicit val rF = relationsFormat(r,r,r, relationFormat[File, String])
	implicit val aF = analysisFormat(stampsFormat, apisFormat, rF)

	val store = AnalysisStore.sync(AnalysisStore.cached(FileBasedStore(cacheDirectory)(aF, setupFormat)))
}