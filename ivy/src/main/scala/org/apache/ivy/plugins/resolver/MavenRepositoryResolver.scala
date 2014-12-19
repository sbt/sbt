package org.apache.ivy.plugins.resolver

import java.io.File
import org.apache.ivy.core.module.descriptor._
import org.apache.ivy.core.module.id.{ ModuleId, ArtifactId, ModuleRevisionId }
import org.apache.ivy.core.report.{ ArtifactDownloadReport, DownloadStatus, MetadataArtifactDownloadReport, DownloadReport }
import org.apache.ivy.core.resolve.{ ResolvedModuleRevision, ResolveData, DownloadOptions }
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.parser.m2.{ PomModuleDescriptorBuilder, ReplaceMavenConfigurationMappings }
import org.apache.ivy.plugins.resolver.util.ResolvedResource
import org.apache.ivy.util.Message
import org.eclipse.aether.artifact.{ DefaultArtifact => AetherArtifact }
import org.eclipse.aether.metadata.{ Metadata, DefaultMetadata }
import org.eclipse.aether.resolution.{
  ArtifactDescriptorRequest => AetherDescriptorRequest,
  ArtifactDescriptorResult => AetherDescriptorResult,
  MetadataRequest => AetherMetadataRequest,
  ArtifactRequest => AetherArtifactRequest
}
import org.eclipse.aether.deployment.{ DeployRequest => AetherDeployRequest }
import org.apache.ivy.core.cache.ArtifactOrigin
import sbt.MavenRepository
import scala.collection.JavaConverters._

object MavenRepositoryResolver {
  val MAVEN_METADATA_XML = "maven-metadata.xml"
}

class MavenRepositoryResolver(repo: MavenRepository, settings: IvySettings) extends AbstractResolver {
  setName(repo.name)

  private val system = MavenRepositorySystemFactory.newRepositorySystemImpl
  // TODO - Maybe create this right before resolving, rathen than every time.
  private val localRepo = new java.io.File(settings.getDefaultIvyUserDir, s"maven-cache")
  sbt.IO.createDirectory(localRepo)
  private val session = MavenRepositorySystemFactory.newSessionImpl(system, localRepo)
  private val aetherRepository = {
    new org.eclipse.aether.repository.RemoteRepository.Builder(repo.name, "default", repo.root).build()
  }

  final val JarPackagings = Set("eclipse-plugin", "hk2-jar", "orbit", "scala-jar", "jar")

  private def aetherCoordsFromMrid(mrid: ModuleRevisionId): String = s"${mrid.getOrganisation}:${mrid.getName}:${mrid.getRevision}"
  // This grabs the dependency for Ivy.
  override def getDependency(dd: DependencyDescriptor, rd: ResolveData): ResolvedModuleRevision = try {
    val request = new AetherDescriptorRequest()
    val coords = aetherCoordsFromMrid(dd.getDependencyRevisionId)
    Message.debug(s"Aether about to resolve [$coords] into [${localRepo.getAbsolutePath}]")
    request.setArtifact(new AetherArtifact(coords))
    request.addRepository(aetherRepository)
    val result = system.readArtifactDescriptor(session, request)
    Message.debug(s"Aether completed, found result - ${result}")

    val metadataRequest = new AetherMetadataRequest()
    metadataRequest.setMetadata(
      new DefaultMetadata(
        dd.getDependencyRevisionId.getOrganisation,
        dd.getDependencyRevisionId.getName,
        dd.getDependencyRevisionId.getRevision,
        MavenRepositoryResolver.MAVEN_METADATA_XML,
        Metadata.Nature.RELEASE_OR_SNAPSHOT))
    metadataRequest.setRepository(aetherRepository)
    val metadataResultOpt =
      try system.resolveMetadata(session, java.util.Arrays.asList(metadataRequest)).asScala.headOption
      catch {
        case e: org.eclipse.aether.resolution.ArtifactResolutionException => None
      }
    // TODO - better pub date.
    val lastModifiedTime = metadataResultOpt match {
      case Some(md) if md.isResolved =>
        Message.warn(s"Maven metadata result: $metadataRequest")
        for (prop <- md.getMetadata.getProperties.asScala) {
          // TODO - figure out how to read these.
          Message.warn(s"$coords - ${prop._1}=${prop._2}")
        }
        0L
      case _ => 0L
    }

    val desc: ModuleDescriptor = {
      // TODO - Default instance will autogenerate artifacts, which we do not want.
      // We should probably create our own instance with no artifacts in it, and add
      // artifacts based on what was requested *or* have aether try to find them.
      val md = DefaultModuleDescriptor.newDefaultInstance(dd.getDependencyRevisionId)

      // Here we add the standard configurations
      for (config <- PomModuleDescriptorBuilder.MAVEN2_CONFIGURATIONS) {
        md.addConfiguration(config)
      }

      /*
      // Here we add our own artifacts.
      // TODO - we need to handle maven classifiers
      val art = new DefaultArtifact(
        dd.getDependencyRevisionId,
        new java.util.Date(lastModifiedTime),
        // TODO - name this based on the name
        result.getArtifact.getArtifactId,
        result.getArtifact.getExtension,
        result.getArtifact.getExtension
      )
      md.addArtifact("master", art)
      */

      // Here we add dependencies.
      for (d <- result.getDependencies.asScala) {
        // TODO - Is this correct for changing detection
        val isChanging = d.getArtifact.getVersion.endsWith("-SNAPSHOT")
        val drid = ModuleRevisionId.newInstance(d.getArtifact.getGroupId, d.getArtifact.getArtifactId, d.getArtifact.getVersion)
        val dd = new DefaultDependencyDescriptor(md, drid, /* force = */ false, isChanging, true) {}
        // TODO - Configuration mappings (are we grabbing scope correctly, or shoudl the default not always be compile?)
        val scope = Option(d.getScope).filterNot(_.isEmpty).getOrElse("compile")
        val mapping = ReplaceMavenConfigurationMappings.addMappings(dd, scope, d.isOptional)
        Message.debug(s"Adding maven transitive dependency ${md.getModuleRevisionId} -> ${dd}")
        md.addDependency(dd)
      }
      // TODO - Dependency management section + dep mediators

      // TODO - Rip out extra attributes

      // TODO - Figure our rd updates

      md.check()

      md
    }

    // Here we need to pretend we downloaded the pom.xml file

    val pom = DefaultArtifact.newPomArtifact(dd.getDependencyRevisionId, new java.util.Date(lastModifiedTime))
    val madr = new MetadataArtifactDownloadReport(pom)
    madr.setSearched(true)
    madr.setDownloadStatus(DownloadStatus.NO) // TODO - Figure this things out for this report.
    new ResolvedModuleRevision(this, this, desc, madr, false /* Force */ )
  } catch {
    case e: org.eclipse.aether.resolution.ArtifactDescriptorException =>
      Message.warn(s"Failed to read descriptor ${dd} from ${repo}, ${e.getMessage}")
      null
    case e: org.eclipse.aether.transfer.ArtifactNotFoundException =>
      Message.debug(s"Failed to find artifact ${dd} from ${repo}, ${e.getMessage}")
      null
    case e: org.eclipse.aether.resolution.ArtifactResolutionException =>
      Message.debug(s"Failed to resolve artifact ${dd} from ${repo}, ${e.getMessage}")
      null
  }

  override def findIvyFileRef(dd: DependencyDescriptor, rd: ResolveData): ResolvedResource = {
    Message.error("Looking for ivy file ref, method not implemented!")
    ???
  }

  override def download(artifacts: Array[Artifact], dopts: DownloadOptions): DownloadReport = {
    val report = new DownloadReport
    val requests =
      for (a <- artifacts) yield {
        val request = new AetherArtifactRequest
        val aetherArt =
          a.getType match {
            case "jar" => new AetherArtifact(aetherCoordsFromMrid(a.getModuleRevisionId))
            case other => new AetherArtifact(s"${aetherCoordsFromMrid(a.getModuleRevisionId)}:$other")
          }
        request.setArtifact(aetherArt)
        request.addRepository(aetherRepository)
        request
      }
    val result =
      try {
        system.resolveArtifacts(session, requests.toList.asJava).asScala
        for ((result, art) <- system.resolveArtifacts(session, requests.toList.asJava).asScala zip artifacts) {
          val adr = new ArtifactDownloadReport(art)
          adr.setDownloadDetails(result.toString)
          // TODO - Fill this out with a real estimate on time...
          adr.setDownloadTimeMillis(0L)
          adr.setArtifactOrigin(new ArtifactOrigin(
            art,
            true,
            repo.name))
          if (result.isMissing) {
            adr.setDownloadStatus(DownloadStatus.FAILED)
            adr.setDownloadDetails(ArtifactDownloadReport.MISSING_ARTIFACT)
          } else if (!result.getExceptions.isEmpty) {
            adr.setDownloadStatus(DownloadStatus.FAILED)
            adr.setDownloadDetails(result.toString)
          } else if (!result.isResolved) {
            adr.setDownloadStatus(DownloadStatus.NO)
          } else {
            val file = result.getArtifact.getFile
            Message.debug(s"Succesffully downloaded: $file")
            adr.setLocalFile(file)
            adr.setSize(file.length)
            adr.setDownloadStatus(DownloadStatus.SUCCESSFUL)
            adr.setDownloadDetails(result.toString)
          }
          report.addArtifactReport(adr)
        }
      } catch {
        case e: org.eclipse.aether.resolution.ArtifactResolutionException =>
          Message.debug(s"Failed to resolve artifacts from ${repo}, ${e.getMessage}")
          for (art <- artifacts) {
            val adr = new ArtifactDownloadReport(art)
            adr.setDownloadDetails("Failed to download")
            adr.setDownloadTimeMillis(0L)
            adr.setDownloadStatus(DownloadStatus.FAILED)
            adr.setArtifactOrigin(new ArtifactOrigin(
              art,
              true,
              repo.name))
          }
      }

    report
  }

  case class PublishTransaction(module: ModuleRevisionId, artifacts: Seq[(Artifact, File)])
  private var currentTransaction: Option[PublishTransaction] = None

  override def beginPublishTransaction(module: ModuleRevisionId, overwrite: Boolean): Unit = {
    // TODO - Set up something to track all artifacts.
    currentTransaction match {
      case Some(t) => throw new IllegalStateException(s"Publish Transaction already open for [$getName]")
      case None    => currentTransaction = Some(PublishTransaction(module, Nil))
    }
  }
  override def abortPublishTransaction(): Unit = {
    // TODO - Delete all published jars
    currentTransaction = None
  }

  def getClassifier(art: Artifact): String = {
    art.getType match {
      case "doc" => "javadoc"
      case "src" => "sources"
      case _     => null
    }
  }

  override def commitPublishTransaction(): Unit = {
    // TODO - actually send all artifacts to aether
    currentTransaction match {
      case Some(t) =>
        Message.debug(s"Publishing module ${t.module}")

        val request = new AetherDeployRequest()
        request.setRepository(aetherRepository)
        for ((art, file) <- t.artifacts) {
          Message.debug(s"  - $art from $file")
          val aetherArtifact = new AetherArtifact(
            t.module.getOrganisation,
            t.module.getName,
            getClassifier(art),
            art.getExt,
            t.module.getRevision,
            // TODO - Use correct Props for sbt plugin deploy.
            Map.empty[String, String].asJava,
            file
          )
          request.addArtifact(aetherArtifact)
        }
        val result = system.deploy(session, request)
      // TODO - Any kind of validity checking?

      case None => throw new IllegalStateException(s"Publish Transaction already open for [$getName]")
    }
  }

  override def publish(art: Artifact, file: File, overwrite: Boolean): Unit = {
    if (currentTransaction.isEmpty)
      currentTransaction match {
        case Some(t) =>
          currentTransaction = Some(t.copy(artifacts = t.artifacts :+ (art, file)))
        case None =>
          throw new IllegalStateException(("MavenRepositories require transactional publish"))
      }
  }

  override def equals(a: Any): Boolean =
    a match {
      case x: MavenRepositoryResolver => x.getName == getName
      case _                          => false
    }
}
