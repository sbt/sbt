/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt.internal.librarymanagement

import java.io.File
import java.util.Date

import org.apache.ivy.core.{ cache, module, report, resolve, search }
import cache.ArtifactOrigin
import search.{ ModuleEntry, OrganisationEntry, RevisionEntry }
import module.id.ModuleRevisionId
import module.descriptor.{
  Artifact => IArtifact,
  DefaultArtifact,
  DependencyDescriptor,
  ModuleDescriptor
}
import org.apache.ivy.plugins.namespace.Namespace
import org.apache.ivy.plugins.resolver.ResolverSettings
import report.{
  ArtifactDownloadReport,
  DownloadReport,
  DownloadStatus,
  MetadataArtifactDownloadReport
}
import resolve.{ DownloadOptions, ResolveData, ResolvedModuleRevision }

/**
 * A Resolver that uses a predefined mapping from module ids to in-memory descriptors.
 * It does not handle artifacts.
 */
class ProjectResolver(name: String, map: Map[ModuleRevisionId, ModuleDescriptor])
    extends ResolverAdapter {
  def getName = name
  def setName(name: String) = sys.error("Setting name not supported by ProjectResolver")
  override def toString = "ProjectResolver(" + name + ", mapped: " + map.keys.mkString(", ") + ")"

  def getDependency(dd: DependencyDescriptor, data: ResolveData): ResolvedModuleRevision =
    getDependency(dd.getDependencyRevisionId).orNull

  private[this] def getDependency(revisionId: ModuleRevisionId): Option[ResolvedModuleRevision] = {
    def constructResult(descriptor: ModuleDescriptor) =
      new ResolvedModuleRevision(this, this, descriptor, report(revisionId), true)
    map get revisionId map constructResult
  }

  private[sbt] def getModuleDescriptor(revisionId: ModuleRevisionId): Option[ModuleDescriptor] =
    map.get(revisionId)

  def report(revisionId: ModuleRevisionId): MetadataArtifactDownloadReport = {
    val artifact = DefaultArtifact.newIvyArtifact(revisionId, new Date)
    val r = new MetadataArtifactDownloadReport(artifact)
    r.setSearched(false)
    r.setDownloadStatus(DownloadStatus.FAILED)
    r
  }

  // this resolver nevers locates artifacts, only resolves dependencies
  def exists(artifact: IArtifact) = false
  def locate(artifact: IArtifact) = null
  def download(artifacts: Array[IArtifact], options: DownloadOptions): DownloadReport = {
    val r = new DownloadReport
    for (artifact <- artifacts)
      if (getDependency(artifact.getModuleRevisionId).isEmpty)
        r.addArtifactReport(notDownloaded(artifact))
    r
  }

  def download(artifact: ArtifactOrigin, options: DownloadOptions): ArtifactDownloadReport =
    notDownloaded(artifact.getArtifact)
  def findIvyFileRef(dd: DependencyDescriptor, data: ResolveData) = null

  def notDownloaded(artifact: IArtifact): ArtifactDownloadReport = {
    val r = new ArtifactDownloadReport(artifact)
    r.setDownloadStatus(DownloadStatus.FAILED)
    r
  }

  // doesn't support publishing
  def publish(artifact: IArtifact, src: File, overwrite: Boolean) =
    sys.error("Publish not supported by ProjectResolver")
  def beginPublishTransaction(module: ModuleRevisionId, overwrite: Boolean): Unit = ()
  def abortPublishTransaction(): Unit = ()
  def commitPublishTransaction(): Unit = ()

  def reportFailure(): Unit = ()
  def reportFailure(art: IArtifact): Unit = ()

  def listOrganisations() = new Array[OrganisationEntry](0)
  def listModules(org: OrganisationEntry) = new Array[ModuleEntry](0)
  def listRevisions(module: ModuleEntry) = new Array[RevisionEntry](0)

  def getNamespace = Namespace.SYSTEM_NAMESPACE

  private[this] var settings: Option[ResolverSettings] = None

  def dumpSettings(): Unit = ()
  def setSettings(settings: ResolverSettings): Unit = { this.settings = Some(settings) }
  def getRepositoryCacheManager = settings match {
    case Some(s) => s.getDefaultRepositoryCacheManager;
    case None    => sys.error("No settings defined for ProjectResolver")
  }
}

object ProjectResolver {
  private[sbt] val InterProject = "inter-project"
}
