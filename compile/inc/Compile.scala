/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbti.api.Source
import java.io.File

object IncrementalCompile
{
	def apply(sources: Set[File], entry: String => Option[File], compile: (Set[File], xsbti.AnalysisCallback) => Unit, previous: Analysis, forEntry: File => Option[Analysis], outputPath: File): (Boolean, Analysis) =
	{
		val current = Stamps.initial(Stamp.exists, Stamp.hash, Stamp.lastModified)
		val internalMap = (f: File) => previous.relations.produced(f).headOption
		val externalAPI = getExternalAPI(entry, forEntry)
		Incremental.compile(sources, entry, previous, current, forEntry, doCompile(compile, internalMap, externalAPI, current, outputPath))
	}
	def doCompile(compile: (Set[File], xsbti.AnalysisCallback) => Unit, internalMap: File => Option[File], externalAPI: (File, String) => Option[Source], current: ReadStamps, outputPath: File) = (srcs: Set[File]) => {
		val callback = new AnalysisCallback(internalMap, externalAPI, current, outputPath)
		compile(srcs, callback)
		callback.get 
	}
	def getExternalAPI(entry: String => Option[File], forEntry: File => Option[Analysis]): (File, String) => Option[Source] =
	 (file: File,className: String) =>
			entry(className) flatMap { defines =>
				if(file != Locate.resolve(defines, className) )
					None
				else
					forEntry(defines) flatMap { analysis =>
						analysis.relations.produced(file).headOption flatMap { src =>
							analysis.apis.internal get src
						}
					}
			}
}
private final class AnalysisCallback(internalMap: File => Option[File], externalAPI: (File, String) => Option[Source], current: ReadStamps, outputPath: File) extends xsbti.AnalysisCallback
{
	override def toString = ( List("APIs", "Binary deps", "Products", "Source deps") zip List(apis, binaryDeps, classes, sourceDeps)).map { case (label, map) => label + "\n\t" + map.mkString("\n\t") }.mkString("\n")
	
	import collection.mutable.{HashMap, HashSet, ListBuffer, Map, Set}
	
	private val apis = new HashMap[File, Source]
	private val binaryDeps = new HashMap[File, Set[File]]
	private val classes = new HashMap[File, Set[File]]
	private val sourceDeps = new HashMap[File, Set[File]]
	private val extSrcDeps = new ListBuffer[(File, String, Source)]
	private val binaryClassName = new HashMap[File, String]

	private def add[A,B](map: Map[A,Set[B]], a: A, b: B): Unit =
		map.getOrElseUpdate(a, new HashSet[B]) += b

	def sourceDependency(dependsOn: File, source: File) = if(source != dependsOn) add(sourceDeps, source, dependsOn)
	def externalBinaryDependency(binary: File, className: String, source: File)
	{
		binaryClassName.put(binary, className)
		add(binaryDeps, source, binary)
	}
	def externalSourceDependency(triple: (File, String, Source)) =  extSrcDeps += triple

	def binaryDependency(classFile: File, name: String, source: File) =
	{
		internalMap(classFile) match
		{
			case Some(dependsOn) =>
				 // dependency is a product of a source not included in this compilation
				sourceDependency(dependsOn, source)
			case None =>
				externalAPI(classFile, name) match
				{
					case Some(api) =>
						// dependency is a product of a source in another project
						externalSourceDependency( (source, name, api) )
					case None =>
						// dependency is some other binary on the classpath
						externalBinaryDependency(classFile, name, source)
				}
		}
	}
		
	def generatedClass(source: File, module: File) = add(classes, source, module)
	
	def api(sourceFile: File, source: Source) { apis(sourceFile) = source }
	def endSource(sourcePath: File): Unit =
		assert(apis.contains(sourcePath))
	
	def get: Analysis = addExternals( addBinaries( addProducts( addSources(Analysis.Empty) ) ) )
	def addProducts(base: Analysis): Analysis = addAll(base, classes)( (a, src, prod) => a.addProduct(src, prod, current product prod ) )
	def addBinaries(base: Analysis): Analysis = addAll(base, binaryDeps)( (a, src, bin) => a.addBinaryDep(src, bin, binaryClassName(bin), current binary bin) )
	def addSources(base: Analysis): Analysis =
		(base /: apis) { case (a, (src, api) ) =>
			a.addSource(src, api, current.internalSource(src), sourceDeps.getOrElse(src, Nil: Iterable[File]))
		}
	def addExternals(base: Analysis): Analysis = (base /: extSrcDeps) { case (a, (source, name, api)) => a.addExternalDep(source, name, api) }
		
	def addAll[A,B](base: Analysis, m: Map[A, Set[B]])( f: (Analysis, A, B) => Analysis): Analysis =
		(base /: m) { case (outer, (a, bs)) =>
			(outer /: bs) { (inner, b) =>
				f(inner, a, b)
		} }

	def beginSource(source: File) {}
}