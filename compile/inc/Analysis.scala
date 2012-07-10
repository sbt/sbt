/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbti.api.Source
import java.io.File

trait Analysis
{
	val stamps: Stamps
	val apis: APIs
	val relations: Relations
	val infos: SourceInfos
	
	def ++(other: Analysis): Analysis
	def -- (sources: Iterable[File]): Analysis
	def copy(stamps: Stamps = stamps, apis: APIs = apis, relations: Relations = relations, infos: SourceInfos = infos): Analysis
	
	def addSource(src: File, api: Source, stamp: Stamp, internalDeps: Iterable[File], info: SourceInfo): Analysis
	def addBinaryDep(src: File, dep: File, className: String, stamp: Stamp): Analysis
	def addExternalDep(src: File, dep: String, api: Source): Analysis
	def addProduct(src: File, product: File, stamp: Stamp, name: String): Analysis

	override lazy val toString = Analysis.summary(this)
}

object Analysis
{
	lazy val Empty: Analysis = new MAnalysis(Stamps.empty, APIs.empty, Relations.empty, SourceInfos.empty)
	def summary(a: Analysis): String =
	{
		val (j, s) = a.apis.allInternalSources.partition(_.getName.endsWith(".java"))
		val c = a.stamps.allProducts
		val ext = a.apis.allExternals
		val jars = a.relations.allBinaryDeps.filter(_.getName.endsWith(".jar"))
		val unreportedCount = a.infos.allInfos.values.map(_.unreportedProblems.size).sum
		val sections =
			counted("Scala source", "", "s", s.size) ++
			counted("Java source", "", "s", j.size) ++
			counted("class", "", "es", c.size) ++
			counted("external source dependenc", "y", "ies", ext.size) ++
			counted("binary dependenc", "y", "ies", jars.size) ++
			counted("unreported warning", "", "s", unreportedCount)
		sections.mkString("Analysis: ", ", ", "")
	}

	def counted(prefix: String, single: String, plural: String, count: Int): Option[String] =
		count match
		{
			case 0 => None
			case 1 => Some("1 " + prefix + single)
			case x => Some(x.toString + " " + prefix + plural)
		}

}
private class MAnalysis(val stamps: Stamps, val apis: APIs, val relations: Relations, val infos: SourceInfos) extends Analysis
{
	def ++ (o: Analysis): Analysis = new MAnalysis(stamps ++ o.stamps, apis ++ o.apis, relations ++ o.relations, infos ++ o.infos)
	def -- (sources: Iterable[File]): Analysis =
	{
		val newRelations = relations -- sources
		def keep[T](f: (Relations, T) => Set[_]): T => Boolean = file => !f(newRelations, file).isEmpty
		
		val newAPIs = apis.removeInternal(sources).filterExt( keep(_ usesExternal _) )
		val newStamps = stamps.filter( keep(_ produced _), sources, keep(_ usesBinary _))
		val newInfos = infos -- sources
		new MAnalysis(newStamps, newAPIs, newRelations, newInfos)
	}
	def copy(stamps: Stamps, apis: APIs, relations: Relations, infos: SourceInfos): Analysis = new MAnalysis(stamps, apis, relations, infos)

	def addSource(src: File, api: Source, stamp: Stamp, internalDeps: Iterable[File], info: SourceInfo): Analysis =
		copy( stamps.markInternalSource(src, stamp), apis.markInternalSource(src, api), relations.addInternalSrcDeps(src, internalDeps), infos.add(src, info) )

	def addBinaryDep(src: File, dep: File, className: String, stamp: Stamp): Analysis =
		copy( stamps.markBinary(dep, className, stamp), apis, relations.addBinaryDep(src, dep), infos )

	def addExternalDep(src: File, dep: String, depAPI: Source): Analysis =
		copy( stamps, apis.markExternalAPI(dep, depAPI), relations.addExternalDep(src, dep), infos )

	def addProduct(src: File, product: File, stamp: Stamp, name: String): Analysis =
		copy( stamps.markProduct(product, stamp), apis, relations.addProduct(src, product, name), infos )
}
