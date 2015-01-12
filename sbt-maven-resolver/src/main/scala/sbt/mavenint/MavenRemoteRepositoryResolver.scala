package sbt
package mavenint

import org.apache.ivy.core.IvyContext
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.settings.IvySettings
import org.eclipse.aether.artifact.{ DefaultArtifact => AetherArtifact }
import org.eclipse.aether.deployment.{ DeployRequest => AetherDeployRequest }
import org.eclipse.aether.metadata.{ DefaultMetadata, Metadata }
import org.eclipse.aether.resolution.{
  ArtifactDescriptorRequest => AetherDescriptorRequest,
  ArtifactDescriptorResult => AetherDescriptorResult,
  ArtifactRequest => AetherArtifactRequest,
  MetadataRequest => AetherMetadataRequest
}
import sbt.ivyint.CustomRemoteMavenResolver
import scala.collection.JavaConverters._

/**
 * A resolver instance which can resolve from a REMOTE maven repository.
 *
 * Note: This creates its *own* local cache directory for cache metadata. using its name.
 *
 */
class MavenRemoteRepositoryResolver(val repo: MavenRepository, settings: IvySettings)
    extends MavenRepositoryResolver(settings) with CustomRemoteMavenResolver {
  setName(repo.name)
  override def toString = s"${repo.name}: ${repo.root}"
  protected val system = MavenRepositorySystemFactory.newRepositorySystemImpl
  // Note:  All maven repository resolvers will use the SAME maven cache.
  //        We're not sure if we care whether or not this means that the wrong resolver may report finding an artifact.
  //        The key is not to duplicate files repeatedly across many caches.
  private val localRepo = new java.io.File(settings.getDefaultIvyUserDir, s"maven-cache")
  sbt.IO.createDirectory(localRepo)
  protected val session = MavenRepositorySystemFactory.newSessionImpl(system, localRepo)
  private val aetherRepository = {
    new org.eclipse.aether.repository.RemoteRepository.Builder(repo.name, SbtRepositoryLayout.LAYOUT_NAME, repo.root).build()
  }
  // TODO - Check if isUseCacheOnly is used correctly.
  private def isUseCacheOnly: Boolean =
    Option(IvyContext.getContext).flatMap(x => Option(x.getResolveData)).flatMap(x => Option(x.getOptions)).map(_.isUseCacheOnly).getOrElse(false)
  protected def addRepositories(request: AetherDescriptorRequest): AetherDescriptorRequest =
    if (isUseCacheOnly) request else request.addRepository(aetherRepository)
  protected def addRepositories(request: AetherArtifactRequest): AetherArtifactRequest =
    if (isUseCacheOnly) request else request.addRepository(aetherRepository)
  /** Actually publishes aether artifacts. */
  protected def publishArtifacts(artifacts: Seq[AetherArtifact]): Unit = {
    val request = new AetherDeployRequest()
    request.setRepository(aetherRepository)
    artifacts foreach request.addArtifact
    system.deploy(session, request)
  }
  protected def getPublicationTime(mrid: ModuleRevisionId): Option[Long] = {
    val metadataRequest = new AetherMetadataRequest()
    metadataRequest.setMetadata(
      new DefaultMetadata(
        mrid.getOrganisation,
        mrid.getName,
        mrid.getRevision,
        MavenRepositoryResolver.MAVEN_METADATA_XML,
        Metadata.Nature.RELEASE_OR_SNAPSHOT))
    if (!isUseCacheOnly) metadataRequest.setRepository(aetherRepository)
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
}
