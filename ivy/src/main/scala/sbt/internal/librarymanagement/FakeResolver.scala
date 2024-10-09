package sbt

import java.io.File
import java.net.URI

import org.apache.ivy.core.cache.ArtifactOrigin
import org.apache.ivy.core.cache.{ DefaultRepositoryCacheManager, RepositoryCacheManager }
import org.apache.ivy.core.module.descriptor.{
  Artifact => IvyArtifact,
  DefaultArtifact,
  DefaultDependencyArtifactDescriptor,
  DefaultModuleDescriptor,
  DependencyArtifactDescriptor,
  DependencyDescriptor
}
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.report.ArtifactDownloadReport
import org.apache.ivy.core.report.{ DownloadReport, DownloadStatus }
import org.apache.ivy.core.report.MetadataArtifactDownloadReport
import org.apache.ivy.core.resolve.{ DownloadOptions, ResolveData, ResolvedModuleRevision }
import org.apache.ivy.core.search.{ ModuleEntry, OrganisationEntry, RevisionEntry }
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.namespace.Namespace
import org.apache.ivy.plugins.resolver.{ DependencyResolver, ResolverSettings }
import org.apache.ivy.plugins.resolver.util.ResolvedResource

import FakeResolver._

/**
 * A fake `DependencyResolver` that statically serves predefined artifacts.
 */
private[sbt] class FakeResolver(private var name: String, cacheDir: File, modules: ModulesMap)
    extends DependencyResolver {

  private object Artifact {
    def unapply(art: IvyArtifact): Some[(String, String, String)] = {
      val revisionID = art.getModuleRevisionId()
      val organisation = revisionID.getOrganisation
      val name = revisionID.getName
      val revision = revisionID.getRevision
      Some((organisation, name, revision))
    }

    def unapply(dd: DependencyDescriptor): Some[(String, String, String)] = {
      val module = dd.getDependencyId()
      val organisation = module.getOrganisation
      val name = module.getName
      val mrid = dd.getDependencyRevisionId()
      val revision = mrid.getRevision()
      Some((organisation, name, revision))
    }
  }

  override def publish(artifact: IvyArtifact, src: File, overwrite: Boolean): Unit =
    throw new UnsupportedOperationException("This resolver doesn't support publishing.")

  override def abortPublishTransaction(): Unit =
    throw new UnsupportedOperationException("This resolver doesn't support publishing.")

  override def beginPublishTransaction(module: ModuleRevisionId, overwrite: Boolean): Unit =
    throw new UnsupportedOperationException("This resolver doesn't support publishing.")

  override def commitPublishTransaction(): Unit =
    throw new UnsupportedOperationException("This resolver doesn't support publishing.")

  override def download(
      artifact: ArtifactOrigin,
      options: DownloadOptions
  ): ArtifactDownloadReport = {

    val report = new ArtifactDownloadReport(artifact.getArtifact)
    val path = new URI(artifact.getLocation).getPath
    val localFile = new File(path)

    if (path.nonEmpty && localFile.exists) {
      report.setLocalFile(localFile)
      report.setDownloadStatus(DownloadStatus.SUCCESSFUL)
      report.setSize(localFile.length)
    } else {
      report.setDownloadStatus(DownloadStatus.FAILED)
    }

    report
  }

  override def download(artifacts: Array[IvyArtifact], options: DownloadOptions): DownloadReport = {
    val report = new DownloadReport

    artifacts foreach { art =>
      Option(locate(art)) foreach (o => report.addArtifactReport(download(o, options)))
    }

    report
  }

  override def dumpSettings(): Unit = ()

  override def exists(artifact: IvyArtifact): Boolean = {
    val Artifact(organisation, name, revision) = artifact
    modules.get((organisation, name, revision)).isDefined
  }

  // This is a fake resolver and we don't have Ivy files. Ivy's spec says we can return `null` if
  // we can't find the module descriptor.
  override def findIvyFileRef(dd: DependencyDescriptor, data: ResolveData): ResolvedResource = null

  override def getDependency(
      dd: DependencyDescriptor,
      data: ResolveData
  ): ResolvedModuleRevision = {

    val Artifact(organisation, name, revision) = dd
    val mrid = dd.getDependencyRevisionId()

    val artifact = modules get ((organisation, name, revision)) map { arts =>
      val artifacts: Array[DependencyArtifactDescriptor] = arts.toArray map (_ artifactOf dd)
      val moduleDescriptor = DefaultModuleDescriptor.newDefaultInstance(mrid, artifacts)
      val defaultArtifact = arts.headOption match {
        case Some(FakeArtifact(name, tpe, ext, _)) =>
          new DefaultArtifact(mrid, new java.util.Date, name, tpe, ext)
        case None => null
      }
      val metadataReport = new MetadataArtifactDownloadReport(defaultArtifact)
      metadataReport.setDownloadStatus(DownloadStatus.SUCCESSFUL)

      new ResolvedModuleRevision(this, this, moduleDescriptor, metadataReport)
    }

    artifact.orNull

  }

  override def getName(): String = name

  override val getNamespace: Namespace = {
    val ns = new Namespace()
    ns.setName(name)
    ns
  }

  override val getRepositoryCacheManager: RepositoryCacheManager = {
    val cacheName = name + "-cache"
    val ivySettings = new IvySettings()
    val baseDir = cacheDir
    new DefaultRepositoryCacheManager(cacheName, ivySettings, baseDir)
  }

  override def listModules(organisation: OrganisationEntry): Array[ModuleEntry] =
    modules.keys.collect {
      case (o, m, _) if o == organisation.getOrganisation =>
        val organisationEntry = new OrganisationEntry(this, o)
        new ModuleEntry(organisationEntry, m)
    }.toArray

  override def listOrganisations(): Array[OrganisationEntry] =
    modules.keys.map { case (o, _, _) => new OrganisationEntry(this, o) }.toArray

  override def listRevisions(module: ModuleEntry): Array[RevisionEntry] =
    modules.keys.collect {
      case (o, m, v) if o == module.getOrganisation && m == module.getModule =>
        new RevisionEntry(module, v)
    }.toArray

  override def listTokenValues(
      tokens: Array[String],
      criteria: java.util.Map[_, _]
  ): Array[java.util.Map[_, _]] =
    Array.empty

  override def listTokenValues(
      token: String,
      otherTokenValues: java.util.Map[_, _]
  ): Array[String] =
    Array.empty

  override def locate(art: IvyArtifact): ArtifactOrigin = {
    val Artifact(moduleOrganisation, moduleName, moduleRevision) = art
    val artifact =
      for {
        artifacts <- modules get ((moduleOrganisation, moduleName, moduleRevision))
        artifact <- artifacts find (a =>
          a.name == art.getName && a.tpe == art.getType && a.ext == art.getExt
        )
      } yield new ArtifactOrigin(art, /* isLocal = */ true, artifact.file.toURI.toURL.toString)

    artifact.orNull

  }

  override def reportFailure(art: IvyArtifact): Unit = ()
  override def reportFailure(): Unit = ()

  override def setName(name: String): Unit = {
    this.name = name
    getNamespace.setName(name)
  }

  override def setSettings(settings: ResolverSettings): Unit = ()

}

private[sbt] object FakeResolver {

  type ModulesMap = Map[(String, String, String), Seq[FakeArtifact]]

  final case class FakeArtifact(name: String, tpe: String, ext: String, file: File) {
    def artifactOf(dd: DependencyDescriptor): DependencyArtifactDescriptor =
      new DefaultDependencyArtifactDescriptor(
        dd,
        name,
        tpe,
        ext,
        file.toURI.toURL,
        new java.util.HashMap
      )
  }
}
