/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbti.api.Source
import java.io.File
import APIs.getAPI

trait APIs
{
	/** The API for the source file `src` at the time represented by this instance.
	* This method returns an empty API if the file had no API or is not known to this instance. */
	def internalAPI(src: File): Source
	/** The API for the external class `ext` at the time represented by this instance.
	* This method returns an empty API if the file had no API or is not known to this instance. */
	def externalAPI(ext: String): Source
	
	def allExternals: collection.Set[String]
	def allInternalSources: collection.Set[File]
	
	def ++ (o: APIs): APIs
	
	def markInternalSource(src: File, api: Source): APIs
	def markExternalAPI(ext: String, api: Source): APIs
	
	def removeInternal(remove: Iterable[File]): APIs
	def filterExt(keep: String => Boolean): APIs
	
	def internal: Map[File, Source]
	def external: Map[String, Source]
}
object APIs
{
	def apply(internal: Map[File, Source], external: Map[String, Source]): APIs = new MAPIs(internal, external)
	def empty: APIs = apply(Map.empty, Map.empty)
	
	val emptyAPI = new xsbti.api.SourceAPI(Array(), Array())
	val emptyCompilation = new xsbti.api.Compilation(-1, "")
	val emptySource = new xsbti.api.Source(emptyCompilation, Array(), emptyAPI, 0)
	def getAPI[T](map: Map[T, Source], src: T): Source = map.getOrElse(src, emptySource)
}

private class MAPIs(val internal: Map[File, Source], val external: Map[String, Source]) extends APIs
{
	def allInternalSources: collection.Set[File] = internal.keySet
	def allExternals: collection.Set[String] = external.keySet
	
	def ++ (o: APIs): APIs = new MAPIs(internal ++ o.internal, external ++ o.external)
	
	def markInternalSource(src: File, api: Source): APIs =
		new MAPIs(internal.updated(src, api), external)

	def markExternalAPI(ext: String, api: Source): APIs =
		new MAPIs(internal, external.updated(ext, api))
	
	def removeInternal(remove: Iterable[File]): APIs = new MAPIs(internal -- remove, external)
	def filterExt(keep: String => Boolean): APIs = new MAPIs(internal, external.filterKeys(keep))
		
	def internalAPI(src: File) = getAPI(internal, src)
	def externalAPI(ext: String) = getAPI(external, ext)
	
}