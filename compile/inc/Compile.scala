/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbti.api.{Source, SourceAPI, Compilation, OutputSetting}
import xsbti.compile.{DependencyChanges, Output, SingleOutput, MultipleOutput}
import xsbti.{Position,Problem,Severity}
import Logger.{m2o, problem}
import java.io.File

object IncrementalCompile
{
	def apply(sources: Set[File], entry: String => Option[File], compile: (Set[File], DependencyChanges, xsbti.AnalysisCallback) => Unit, previous: Analysis, forEntry: File => Option[Analysis], output: Output, log: Logger): (Boolean, Analysis) =
	{
		val current = Stamps.initial(Stamp.exists, Stamp.hash, Stamp.lastModified)
		val internalMap = (f: File) => previous.relations.produced(f).headOption
		val externalAPI = getExternalAPI(entry, forEntry)
		Incremental.compile(sources, entry, previous, current, forEntry, doCompile(compile, internalMap, externalAPI, current, output), log)
	}
	def doCompile(compile: (Set[File], DependencyChanges, xsbti.AnalysisCallback) => Unit, internalMap: File => Option[File], externalAPI: (File, String) => Option[Source], current: ReadStamps, output: Output) = (srcs: Set[File], changes: DependencyChanges) => {
		val callback = new AnalysisCallback(internalMap, externalAPI, current, output)
		compile(srcs, changes, callback)
		callback.get 
	}
	def getExternalAPI(entry: String => Option[File], forEntry: File => Option[Analysis]): (File, String) => Option[Source] =
	 (file: File,className: String) =>
			entry(className) flatMap { defines =>
				if(file != Locate.resolve(defines, className) )
					None
				else
					forEntry(defines) flatMap { analysis =>
						analysis.relations.definesClass(className).headOption flatMap { src =>
							analysis.apis.internal get src
						}
					}
			}
}
private final class AnalysisCallback(internalMap: File => Option[File], externalAPI: (File, String) => Option[Source], current: ReadStamps, output: Output) extends xsbti.AnalysisCallback
{
	val compilation = {
		val outputSettings = output match {
			case single: SingleOutput => Array(new OutputSetting("/", single.outputDirectory.getAbsolutePath))
			case multi: MultipleOutput =>
				multi.outputGroups.map(out => new OutputSetting(out.sourceDirectory.getAbsolutePath, out.outputDirectory.getAbsolutePath)).toArray
		}
		new Compilation(System.currentTimeMillis, outputSettings)
	}

	override def toString = ( List("APIs", "Binary deps", "Products", "Source deps") zip List(apis, binaryDeps, classes, sourceDeps)).map { case (label, map) => label + "\n\t" + map.mkString("\n\t") }.mkString("\n")
	
	import collection.mutable.{HashMap, HashSet, ListBuffer, Map, Set}
	
	private[this] val apis = new HashMap[File, (Int, SourceAPI)]
	private[this] val unreporteds = new HashMap[File, ListBuffer[Problem]]
	private[this] val reporteds = new HashMap[File, ListBuffer[Problem]]
	private[this] val binaryDeps = new HashMap[File, Set[File]]
		 // source file to set of generated (class file, class name)
	private[this] val classes = new HashMap[File, Set[(File, String)]]
		 // generated class file to its source file
	private[this] val classToSource = new HashMap[File, File]
	private[this] val sourceDeps = new HashMap[File, Set[File]]
	private[this] val extSrcDeps = new ListBuffer[(File, String, Source)]
	private[this] val binaryClassName = new HashMap[File, String]
		 // source files containing a macro def.
	private[this] val macroSources = Set[File]()

	private def add[A,B](map: Map[A,Set[B]], a: A, b: B): Unit =
		map.getOrElseUpdate(a, new HashSet[B]) += b

	def problem(category: String, pos: Position, msg: String, severity: Severity, reported: Boolean): Unit =
	{
		for(source <- m2o(pos.sourceFile)) {
			val map = if(reported) reporteds else unreporteds
			map.getOrElseUpdate(source, ListBuffer.empty) += Logger.problem(category, pos, msg, severity)
		}
	}

	def sourceDependency(dependsOn: File, source: File) = if(source != dependsOn) add(sourceDeps, source, dependsOn)
	def externalBinaryDependency(binary: File, className: String, source: File)
	{
		binaryClassName.put(binary, className)
		add(binaryDeps, source, binary)
	}
	def externalSourceDependency(triple: (File, String, Source)) =  extSrcDeps += triple

	def binaryDependency(classFile: File, name: String, source: File) =
		internalMap(classFile) match
		{
			case Some(dependsOn) =>
				 // dependency is a product of a source not included in this compilation
				sourceDependency(dependsOn, source)
			case None =>
				classToSource.get(classFile) match
				{
					case Some(dependsOn) =>
						// dependency is a product of a source in this compilation step,
						//  but not in the same compiler run (as in javac v. scalac)
						sourceDependency(dependsOn, source)
					case None =>
						externalDependency(classFile, name, source)
				}
		}

	private[this] def externalDependency(classFile: File, name: String, source: File): Unit =
		externalAPI(classFile, name) match
		{
			case Some(api) =>
				// dependency is a product of a source in another project
				externalSourceDependency( (source, name, api) )
			case None =>
				// dependency is some other binary on the classpath
				externalBinaryDependency(classFile, name, source)
		}
		
	def generatedClass(source: File, module: File, name: String) =
	{
		add(classes, source, (module, name))
		classToSource.put(module, source)
	}
	
	def api(sourceFile: File, source: SourceAPI) {
		import xsbt.api.{APIUtil, HashAPI}
		if (APIUtil.hasMacro(source)) macroSources += sourceFile
		apis(sourceFile) = (HashAPI(source), APIUtil.minimize(source))
	}

	def endSource(sourcePath: File): Unit =
		assert(apis.contains(sourcePath))
	
	def get: Analysis = addExternals( addBinaries( addProducts( addSources(Analysis.Empty) ) ) )
	def addProducts(base: Analysis): Analysis = addAll(base, classes) { case (a, src, (prod, name)) => a.addProduct(src, prod, current product prod, name ) }
	def addBinaries(base: Analysis): Analysis = addAll(base, binaryDeps)( (a, src, bin) => a.addBinaryDep(src, bin, binaryClassName(bin), current binary bin) )
	def addSources(base: Analysis): Analysis =
		(base /: apis) { case (a, (src, api) ) =>
			val stamp = current.internalSource(src)
			val hash = stamp match { case h: Hash => h.value; case _ => new Array[Byte](0) }
			// TODO store this in Relations, rather than Source.
			val hasMacro: Boolean = macroSources.contains(src)
			val s = new xsbti.api.Source(compilation, hash, api._2, api._1, hasMacro)
			val info = SourceInfos.makeInfo(getOrNil(reporteds, src), getOrNil(unreporteds, src))
			a.addSource(src, s, stamp, sourceDeps.getOrElse(src, Nil: Iterable[File]), info)
		}
	def getOrNil[A,B](m: collection.Map[A, Seq[B]], a: A): Seq[B] = m.get(a).toList.flatten
	def addExternals(base: Analysis): Analysis = (base /: extSrcDeps) { case (a, (source, name, api)) => a.addExternalDep(source, name, api) }
		
	def addAll[A,B](base: Analysis, m: Map[A, Set[B]])( f: (Analysis, A, B) => Analysis): Analysis =
		(base /: m) { case (outer, (a, bs)) =>
			(outer /: bs) { (inner, b) =>
				f(inner, a, b)
		} }

	def beginSource(source: File) {}
}
