package sbt.internal.librarymanagement

import java.io.File
import org.apache.ivy.core
import org.apache.ivy.plugins.parser
import core.IvyPatternHelper
import core.settings.IvySettings
import core.cache.ResolutionCacheManager
import core.module.id.ModuleRevisionId
import core.module.descriptor.ModuleDescriptor
import ResolutionCache.{ Name, ReportDirectory, ResolvedName, ResolvedPattern }
import parser.xml.XmlModuleDescriptorParser
import sbt.io.IO
import sbt.librarymanagement.*

/**
 * Replaces the standard Ivy resolution cache in order to:
 * 1. Separate cached resolved Ivy files from resolution reports, making the resolution reports easier to find.
 * 2. Have them per-project for easier cleaning (possible with standard cache, but central to this custom one).
 * 3. Cache location includes extra attributes so that cross builds of a plugin do not overwrite each other.
 */
private[sbt] final class ResolutionCache(base: File, settings: IvySettings)
    extends ResolutionCacheManager {
  private def resolvedFileInCache(m: ModuleRevisionId, name: String, ext: String): File = {
    val p = ResolvedPattern
    val f = IvyPatternHelper.substitute(
      p,
      m.getOrganisation,
      m.getName,
      m.getBranch,
      m.getRevision,
      name,
      name,
      ext,
      null,
      null,
      m.getAttributes,
      null
    )
    new File(base, f)
  }
  private val reportBase: File = new File(base, ReportDirectory)

  def getResolutionCacheRoot: File = base
  def clean(): Unit = IO.delete(base)
  override def toString = Name

  def getResolvedIvyFileInCache(mrid: ModuleRevisionId): File =
    resolvedFileInCache(mrid, ResolvedName, "xml")
  def getResolvedIvyPropertiesInCache(mrid: ModuleRevisionId): File =
    resolvedFileInCache(mrid, ResolvedName, "properties")
  // name needs to be the same as Ivy's default because the ivy-report.xsl stylesheet assumes this
  //   when making links to reports for other configurations
  def getConfigurationResolveReportInCache(resolveId: String, conf: String): File =
    new File(reportBase, resolveId + "-" + conf + ".xml")
  def getConfigurationResolveReportsInCache(resolveId: String): Array[File] =
    IO.listFiles(reportBase).filter(_.getName.startsWith(resolveId + "-"))

  // XXX: this method is required by ResolutionCacheManager in Ivy 2.3.0 final,
  // but it is apparently unused by Ivy as sbt uses Ivy.  Therefore, it is
  // unexercised in tests.  Note that the implementation of this method in Ivy 2.3.0's
  // DefaultResolutionCache also resolves parent properties for a given mrid
  def getResolvedModuleDescriptor(mrid: ModuleRevisionId): ModuleDescriptor = {
    val ivyFile = getResolvedIvyFileInCache(mrid)
    if (!ivyFile.exists()) {
      throw new IllegalStateException("Ivy file not found in cache for " + mrid + "!")
    }

    XmlModuleDescriptorParser.getInstance().parseDescriptor(settings, ivyFile.toURI.toURL, false)
  }

  def saveResolvedModuleDescriptor(md: ModuleDescriptor): Unit = {
    val mrid = md.getResolvedModuleRevisionId
    val cachedIvyFile = getResolvedIvyFileInCache(mrid)
    md.toIvyFile(cachedIvyFile)
  }
}
private[sbt] object ResolutionCache {

  /**
   * Removes cached files from the resolution cache for the module with ID `mrid`
   * and the resolveId (as set on `ResolveOptions`).
   */
  private[sbt] def cleanModule(
      mrid: ModuleRevisionId,
      resolveId: String,
      manager: ResolutionCacheManager
  ): Unit = {
    val files =
      Option(manager.getResolvedIvyFileInCache(mrid)).toList :::
        Option(manager.getResolvedIvyPropertiesInCache(mrid)).toList :::
        Option(manager.getConfigurationResolveReportsInCache(resolveId)).toList.flatten
    IO.delete(files)
  }

  private val ReportDirectory = "reports"

  // base name (name except for extension) of resolution report file
  private val ResolvedName = "resolved.xml"

  // Cache name
  private val Name = "sbt-resolution-cache"

  // use sbt-specific extra attributes so that resolved xml files do not get overwritten when using different Scala/sbt versions
  private val ResolvedPattern =
    "[organisation]/[module]/" + Resolver.PluginPattern + "([branch]/)[revision]/[artifact].[ext]"
}
