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
import org.eclipse.aether.resolution.{
  ArtifactDescriptorRequest => AetherDescriptorRequest,
  ArtifactDescriptorResult => AetherDescriptorResult,
  ArtifactRequest => AetherArtifactRequest
}
import sbt.MavenRepository
import scala.collection.JavaConverters._

class MavenRepositoryResolver(repo: MavenRepository, settings: IvySettings) extends AbstractResolver {
  setName(repo.name)

  private val system = MavenRepositorySystemFactory.newRepositorySystemImpl
  // TODO - Maybe create this right before resolving, rathen than every time.
  private val localRepo = new java.io.File(settings.getDefaultIvyUserDir, s"pom-resolver")
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
    Message.warn(s"Aether about to resolve [$coords] into [${localRepo.getAbsolutePath}]")
    request.setArtifact(new AetherArtifact(coords))
    request.addRepository(aetherRepository)
    val result = system.readArtifactDescriptor(session, request)
    Message.warn(s"Aether completed, found result - ${result}")

    val desc: ModuleDescriptor = {
      val md = DefaultModuleDescriptor.newDefaultInstance(dd.getDependencyRevisionId)

      // Here we add the standard configurations
      for (config <- PomModuleDescriptorBuilder.MAVEN2_CONFIGURATIONS) {
        md.addConfiguration(config)
      }

      // Here we add our own artifacts.
      val art = new DefaultArtifact(
        dd.getDependencyRevisionId,
        new java.util.Date(),
        // TODO - name this based on the name
        result.getArtifact.getArtifactId,
        result.getArtifact.getExtension,
        result.getArtifact.getExtension
      )
      md.addArtifact("master", art)

      // Here we add dependencies.
      for (d <- result.getDependencies.asScala) {
        // TODO - Is this correct for changing detection
        val isChanging = d.getArtifact.getVersion.endsWith("-SNAPSHOT")
        val drid = ModuleRevisionId.newInstance(d.getArtifact.getGroupId, d.getArtifact.getArtifactId, d.getArtifact.getVersion)
        val dd = new DefaultDependencyDescriptor(md, drid, /* force = */ false, isChanging, true) {}
        // TODO - Configuration mappings (are we grabbing scope correctly, or shoudl the default not always be compile?)
        val scope = Option(d.getScope).filterNot(_.isEmpty).getOrElse("compile")
        val mapping = ReplaceMavenConfigurationMappings.addMappings(dd, scope, d.isOptional)
        Message.warn(s"Adding maven transitive dependency ${md.getModuleRevisionId} -> ${dd}")
        md.addDependency(dd)
      }
      // TODO - Dependency management section + dep mediators

      // TODO - Rip out extra attributes

      md
    }
    // TODO - better pub date.
    val metadataArtifact: Artifact = DefaultArtifact.newPomArtifact(dd.getDependencyRevisionId, new java.util.Date(0L))
    val report: MetadataArtifactDownloadReport = new MetadataArtifactDownloadReport(metadataArtifact)
    report.setSearched(true)
    report.setOriginalLocalFile(result.getArtifact.getFile)
    new ResolvedModuleRevision(this, this, desc, report, false /* Force */ )
  } catch {
    case e: org.eclipse.aether.resolution.ArtifactDescriptorException =>
      Message.warn(s"Failed to resolve ${dd} from ${repo}, ${e.getMessage}")
      e.printStackTrace()
      Option(e.getCause) foreach (_.printStackTrace())
      null
  }

  override def findIvyFileRef(dd: DependencyDescriptor, rd: ResolveData): ResolvedResource = ???

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
    for ((result, art) <- system.resolveArtifacts(session, requests.toList.asJava).asScala zip artifacts) {
      val adr = new ArtifactDownloadReport(art)
      adr.setDownloadDetails(result.toString)
      // TODO - Fill this out with a real estimate on time...
      adr.setDownloadTimeMillis(0L)
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
        Message.warn(s"Succesffully downloaded: $file")
        adr.setLocalFile(file)
        adr.setSize(file.length)
        adr.setDownloadStatus(DownloadStatus.SUCCESSFUL)
        adr.setDownloadDetails(result.toString)
      }
      report.addArtifactReport(adr)
    }
    report
  }
  override def publish(art: Artifact, file: File, overwrite: Boolean): Unit = ???

  override def equals(a: Any): Boolean =
    a match {
      case x: MavenRepositoryResolver => x.getName == getName
      case _                          => false
    }
}
