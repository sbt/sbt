package sbt

import java.io.File
import org.apache.ivy.core
import core.IvyPatternHelper
import core.cache.{CacheMetadataOptions, DefaultRepositoryCacheManager, DefaultResolutionCacheManager, ResolutionCacheManager}
import core.module.id.ModuleRevisionId
import ResolutionCache.{Name, ReportDirectory, ResolvedName, ResolvedPattern}

/** Replaces the standard Ivy resolution cache in order to:
* 1. Separate cached resolved Ivy files from resolution reports, making the resolution reports easier to find.
* 2. Have them per-project for easier cleaning (possible with standard cache, but central to this custom one).
* 3. Cache location includes extra attributes so that cross builds of a plugin do not overwrite each other.
*/
private[sbt] final class ResolutionCache(base: File) extends ResolutionCacheManager
{
	private[this] def resolvedFileInCache(m: ModuleRevisionId, name: String, ext: String): File = {
		val p = ResolvedPattern
		val f = IvyPatternHelper.substitute(p, m.getOrganisation, m.getName, m.getBranch, m.getRevision, name, name, ext, null, null, m.getAttributes, null)
		new File(base, f)
	}
	private[this] def reportBase(resolveId: String): File = 
		new File(new File(base, ReportDirectory), resolveId)

	def getResolutionCacheRoot: File = base
	def clean() { IO.delete(base) }
	override def toString = Name

	def getResolvedIvyFileInCache(mrid: ModuleRevisionId): File =
		resolvedFileInCache(mrid, ResolvedName, "xml")
	def getResolvedIvyPropertiesInCache(mrid: ModuleRevisionId): File =
		resolvedFileInCache(mrid, ResolvedName, "properties")
	def getConfigurationResolveReportInCache(resolveId: String, conf: String): File =
		new File(reportBase(resolveId), "/" + conf + "-" + ResolvedName)
	def getConfigurationResolveReportsInCache(resolveId: String): Array[File] =
		IO.listFiles(reportBase(resolveId)).flatMap(d => IO.listFiles(d))
}
private[sbt] object ResolutionCache
{
	/** Removes cached files from the resolution cache for the module with ID `mrid`
	* and the resolveId (as set on `ResolveOptions`).  */
	private[sbt] def cleanModule(mrid: ModuleRevisionId, resolveId: String, manager: ResolutionCacheManager)
	{
		val files =
			Option(manager.getResolvedIvyFileInCache(mrid)).toList :::
			Option(manager.getResolvedIvyPropertiesInCache(mrid)).toList :::
			Option(manager.getConfigurationResolveReportsInCache(resolveId)).toList.flatten
		IO.delete(files)
	}

	private val ReportDirectory = "reports"

		// name of the file providing a dependency resolution report for a configuration
	private val ReportFileName = "report.xml"

		// base name (name except for extension) of resolution report file
	private val ResolvedName = "resolved.xml"

		// Cache name
	private val Name = "sbt-resolution-cache"

		// use sbt-specific extra attributes so that resolved xml files do not get overwritten when using different Scala/sbt versions
	private val ResolvedPattern = "[organisation]/[module]/" + Resolver.PluginPattern + "[revision]/[artifact].[ext]"
}