/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbti.api.Source
import java.io.File

object IncrementalCompile
{
	def apply(sources: Set[File], compile: (Set[File], xsbti.AnalysisCallback) => Unit, previous: Analysis, externalAPI: String => Source): Analysis =
	{
		val current = Stamps.initial(Stamp.exists, Stamp.hash, Stamp.lastModified)
		val internalMap = (f: File) => previous.relations.produced(f).headOption
		Incremental.compile(sources, previous, current, externalAPI, doCompile(compile, internalMap, current))
	}
	def doCompile(compile: (Set[File], xsbti.AnalysisCallback) => Unit, internalMap: File => Option[File], current: ReadStamps) = (srcs: Set[File]) => {
		val callback = new AnalysisCallback(internalMap, current)
		compile(srcs, callback)
		callback.get 
	}
}
private final class AnalysisCallback(internalMap: File => Option[File], current: ReadStamps) extends xsbti.AnalysisCallback
{
	import collection.mutable.{HashMap, HashSet, Map, Set}
	
	private val apis = new HashMap[File, Source]
	private val binaryDeps = new HashMap[File, Set[File]]
	private val classes = new HashMap[File, Set[File]]
	private val sourceDeps = new HashMap[File, Set[File]]

	private def add[A,B](map: Map[A,Set[B]], a: A, b: B): Unit =
		map.getOrElseUpdate(a, new HashSet[B]) += b

	def sourceDependency(dependsOn: File, source: File) = add(sourceDeps, source, dependsOn)
	
	def jarDependency(jar: File, source: File) = add(binaryDeps, source, jar)
	def classDependency(clazz: File, source: File) = add(binaryDeps, source, clazz)
	
	def productDependency(classFile: File, sourcePath: File) =
		internalMap(classFile) match {
			case Some(dependsOn) => sourceDependency(dependsOn, sourcePath)
			case None => classDependency(classFile, sourcePath)
		}
		
	def generatedClass(source: File, module: File) = add(classes, source, module)
	
	def api(sourceFile: File, source: Source) { apis(sourceFile) = source }
	def endSource(sourcePath: File): Unit =
		assert(apis.contains(sourcePath))
	
	def get: Analysis = addBinaries( addProducts( addSources(Analysis.Empty) ) )
	def addProducts(base: Analysis): Analysis = addAll(base, classes)( (a, src, prod) => a.addProduct(src, prod, current product prod ) )
	def addBinaries(base: Analysis): Analysis = addAll(base, binaryDeps)( (a, src, bin) => a.addBinaryDep(src, bin, current binary bin) )
	def addSources(base: Analysis): Analysis =
		(base /: apis) { case (a, (src, api) ) =>
			a.addSource(src, api, current.internalSource(src), sourceDeps.getOrElse(src, Nil: Iterable[File]))
		}
		
	def addAll[A,B](base: Analysis, m: Map[A, Set[B]])( f: (Analysis, A, B) => Analysis): Analysis =
		(base /: m) { case (outer, (a, bs)) =>
			(outer /: bs) { (inner, b) =>
				f(inner, a, b)
		} }
	
	private def emptyS = new Array[String](0)
	def superclassNames = emptyS
	def annotationNames = emptyS
	def superclassNotFound(superclassName: String) {}
	def foundSubclass(source: File, subclassName: String, superclassName: String, isModule: Boolean) {}
	def foundAnnotated(source: File, className: String, annotationName: String, isModule: Boolean) {}
	def foundApplication(source: File, className: String) {}
	def beginSource(source: File) {}
}