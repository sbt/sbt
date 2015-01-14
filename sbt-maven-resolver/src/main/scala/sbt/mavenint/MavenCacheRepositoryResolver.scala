package sbt
package mavenint

import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.settings.IvySettings
import org.eclipse.aether.artifact.{ DefaultArtifact => AetherArtifact }
import org.eclipse.aether.installation.{ InstallRequest => AetherInstallRequest }
import org.eclipse.aether.metadata.{ DefaultMetadata, Metadata }
import org.eclipse.aether.resolution.{
  ArtifactDescriptorRequest => AetherDescriptorRequest,
  ArtifactRequest => AetherArtifactRequest,
  MetadataRequest => AetherMetadataRequest
}
import sbt.ivyint.CustomMavenResolver

import scala.collection.JavaConverters._

/**
 * A resolver instance which can resolve from a maven CACHE.
 *
 * Note: This should never hit somethign remote, as it just looks in the maven cache for things already resolved.
 */
class MavenCacheRepositoryResolver(val repo: MavenCache, settings: IvySettings)
    extends MavenRepositoryResolver(settings) with CustomMavenResolver {
  setName(repo.name)
  protected val system = MavenRepositorySystemFactory.newRepositorySystemImpl
  sbt.IO.createDirectory(repo.rootFile)
  protected val session = MavenRepositorySystemFactory.newSessionImpl(system, repo.rootFile)
  protected def setRepository(request: AetherMetadataRequest): AetherMetadataRequest = request
  protected def addRepositories(request: AetherDescriptorRequest): AetherDescriptorRequest = request
  protected def addRepositories(request: AetherArtifactRequest): AetherArtifactRequest = request
  protected def publishArtifacts(artifacts: Seq[AetherArtifact]): Unit = {
    val request = new AetherInstallRequest()
    artifacts foreach request.addArtifact
    system.install(session, request)
  }
  // TODO - Share this with non-local repository code, since it's MOSTLY the same.
  protected def getPublicationTime(mrid: ModuleRevisionId): Option[Long] = {
    val metadataRequest = new AetherMetadataRequest()
    metadataRequest.setMetadata(
      new DefaultMetadata(
        mrid.getOrganisation,
        mrid.getName,
        mrid.getRevision,
        MavenRepositoryResolver.MAVEN_METADATA_XML,
        Metadata.Nature.RELEASE_OR_SNAPSHOT))
    val metadataResultOpt =
      try system.resolveMetadata(session, java.util.Arrays.asList(metadataRequest)).asScala.headOption
      catch {
        case e: org.eclipse.aether.resolution.ArtifactResolutionException => None
      }
    try metadataResultOpt match {
      case Some(md) if md.isResolved =>
        import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
        import org.codehaus.plexus.util.ReaderFactory
        val readMetadata = {
          val reader = ReaderFactory.newXmlReader(md.getMetadata.getFile)
          try new MetadataXpp3Reader().read(reader, false)
          finally reader.close()
        }
        val timestampOpt =
          for {
            v <- Option(readMetadata.getVersioning)
            sp <- Option(v.getSnapshot)
            ts <- Option(sp.getTimestamp)
            t <- MavenRepositoryResolver.parseTimeString(ts)
          } yield t
        val lastUpdatedOpt =
          for {
            v <- Option(readMetadata.getVersioning)
            lu <- Option(v.getLastUpdated)
            d <- MavenRepositoryResolver.parseTimeString(lu)
          } yield d
        // TODO - Only look at timestamp *IF* the version is for a snapshot.
        timestampOpt orElse lastUpdatedOpt
      case _ => None
    }
  }
  override def toString = s"${repo.name}: ${repo.root}"
}
