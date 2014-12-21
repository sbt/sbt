package org.apache.ivy.plugins.resolver

import java.io.File
import java.util.Date
import org.apache.ivy.core.module.descriptor._
import org.apache.ivy.core.module.id.{ ModuleId, ArtifactId, ModuleRevisionId }
import org.apache.ivy.core.report.{ ArtifactDownloadReport, DownloadStatus, MetadataArtifactDownloadReport, DownloadReport }
import org.apache.ivy.core.resolve.{ ResolvedModuleRevision, ResolveData, DownloadOptions }
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import org.apache.ivy.plugins.parser.m2.{ PomModuleDescriptorBuilder, ReplaceMavenConfigurationMappings }
import org.apache.ivy.plugins.resolver.MavenRepositoryResolver.JarPackaging
import org.apache.ivy.plugins.resolver.util.ResolvedResource
import org.apache.ivy.util.Message
import org.apache.maven.repository.internal.SbtExtraProperties
import org.eclipse.aether.artifact.{ DefaultArtifact => AetherArtifact }
import org.eclipse.aether.metadata.{ Metadata, DefaultMetadata }
import org.eclipse.aether.resolution.{ ArtifactDescriptorRequest => AetherDescriptorRequest, ArtifactDescriptorResult => AetherDescriptorResult, MetadataRequest => AetherMetadataRequest, ArtifactRequest => AetherArtifactRequest, ArtifactResolutionException }
import org.eclipse.aether.deployment.{ DeployRequest => AetherDeployRequest }
import org.apache.ivy.core.cache.ArtifactOrigin
import sbt.MavenRepository
import scala.collection.JavaConverters._

object MavenRepositoryResolver {
  val MAVEN_METADATA_XML = "maven-metadata.xml"
  val CLASSIFIER_ATTRIBUTE = "e:classifier"

  val JarPackagings = Set("eclipse-plugin", "hk2-jar", "orbit", "scala-jar", "jar", "bundle")

  object JarPackaging {
    def unapply(in: String): Boolean = JarPackagings.contains(in)
  }
}

class MavenRepositoryResolver(val repo: MavenRepository, settings: IvySettings) extends AbstractResolver {
  setName(repo.name)

  private val system = MavenRepositorySystemFactory.newRepositorySystemImpl
  // TODO - Maybe create this right before resolving, rathen than every time.
  private val localRepo = new java.io.File(settings.getDefaultIvyUserDir, s"maven-cache")
  sbt.IO.createDirectory(localRepo)
  private val session = MavenRepositorySystemFactory.newSessionImpl(system, localRepo)
  private val aetherRepository = {
    new org.eclipse.aether.repository.RemoteRepository.Builder(repo.name, "default", repo.root).build()
  }

  // TOOD - deal with packaging here.
  private def aetherCoordsFromMrid(mrid: ModuleRevisionId): String = s"${mrid.getOrganisation}:${mrid.getName}:${mrid.getRevision}"
  private def aetherCoordsFromMrid(mrid: ModuleRevisionId, packaging: String): String = s"${mrid.getOrganisation}:${mrid.getName}:${mrid.getRevision}"

  // Handles appending licenses to the module descriptor fromthe pom.
  private def addLicenseInfo(md: DefaultModuleDescriptor, map: java.util.Map[String, AnyRef]) = {
    val count = map.get(SbtExtraProperties.LICENSE_COUNT_KEY) match {
      case null                 => 0
      case x: java.lang.Integer => x.intValue
      case x: String            => x.toInt
      case _                    => 0
    }
    for {
      i <- 0 until count
      name <- Option(map.get(SbtExtraProperties.makeLicenseName(i))).map(_.toString)
      url <- Option(map.get(SbtExtraProperties.makeLicenseUrl(i))).map(_.toString)
    } md.addLicense(new License(name, url))
  }

  // This grabs the dependency for Ivy.
  override def getDependency(dd: DependencyDescriptor, rd: ResolveData): ResolvedModuleRevision = try {
    // TODO - Check to see if we're asking for latest.* version, and if so, we should run a latest version query
    //        first and use that result to return the metadata/final module.
    Message.debug(s"Requesting conf [${dd.getModuleConfigurations.mkString(",")}] from Aether module ${dd.getDependencyRevisionId}")

    val request = new AetherDescriptorRequest()
    val coords = aetherCoordsFromMrid(dd.getDependencyRevisionId)
    Message.debug(s"Aether about to resolve [$coords] into [${localRepo.getAbsolutePath}]")
    request.setArtifact(new AetherArtifact(coords))
    request.addRepository(aetherRepository)
    val result = system.readArtifactDescriptor(session, request)
    val packaging = getPackagingFromPomProperties(result.getProperties)
    Message.debug(s"Aether resolved ${dd.getDependencyId} w/ packaging ${packaging}")

    // TODO - grab the parsed packaging attribute from the metadata to figure out if we should FORCE
    //        the expectation of a jar file or not.

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

    // Construct a new Ivy module descriptor
    val desc: ModuleDescriptor = {
      // TODO - Default instance will autogenerate artifacts, which we do not want.
      // We should probably create our own instance with no artifacts in it, and add
      // artifacts based on what was requested *or* have aether try to find them.
      // TODO - Better detection of snapshot and handling latest.integratoin/latest.snapshot
      val status =
        if (dd.getDependencyRevisionId.getRevision.endsWith("-SNAPSHOT")) "release"
        else "integration"
      val md =
        new DefaultModuleDescriptor(dd.getDependencyRevisionId, status, null, false)
      //DefaultModuleDescriptor.newDefaultInstance(dd.getDependencyRevisionId)
      // Here we add the standard configurations
      for (config <- PomModuleDescriptorBuilder.MAVEN2_CONFIGURATIONS) {
        md.addConfiguration(config)
      }

      // Here we look into the artifacts specified from the dependency descriptor *and* those that are defaulted,
      // and append them to the appropriate configurations.
      addArtifactsFromPom(dd, packaging, md)
      // Here we add dependencies.
      addDependenciesFromAether(result, md)
      // Here we use pom.xml Dependency management section to create Ivy dependency mediators.
      addManagedDependenciesFromAether(result, md)
      // Here we rip out license info.
      addLicenseInfo(md, result.getProperties)
      // TODO - Rip out extra attributes
      // TODO - is this the correct way to add extra info?
      md.addExtraInfo(SbtExtraProperties.MAVEN_PACKAGING_KEY, packaging)
      // TODO - Figure our rd updates
      md.check()
      md
    }

    // Here we need to pretend we downloaded the pom.xml file

    val pom = DefaultArtifact.newPomArtifact(dd.getDependencyRevisionId, new java.util.Date(lastModifiedTime))
    val madr = new MetadataArtifactDownloadReport(pom)
    madr.setSearched(true)
    madr.setDownloadStatus(DownloadStatus.SUCCESSFUL) // TODO - Figure this things out for this report.
    Message.debug(s"Returning resolved module information with artifacts = ${desc.getAllArtifacts.mkString(", ")}")
    new ResolvedModuleRevision(this, this, desc, madr, false /* Force */ )
  } catch {
    case e: org.eclipse.aether.resolution.ArtifactDescriptorException =>
      Message.warn(s"Failed to read descriptor ${dd} from ${repo}, ${e.getMessage}")
      null
  }

  /** Determines which artifacts are associated with this maven module and appends them to the descriptor. */
  def addArtifactsFromPom(dd: DependencyDescriptor, packaging: String, md: DefaultModuleDescriptor) {
    Message.debug(s"Calculating artifacts for ${dd.getDependencyId} w/ packaging $packaging")
    // Here we add in additional artifact requests, which ALLWAYS have to be explicit since
    // Maven/Aether doesn't include all known artifacts in a pom.xml
    // TODO - This does not appear to be working correctly.
    if (dd.getAllDependencyArtifacts.isEmpty) {
      val artifactId = s"${dd.getDependencyId.getName}-${dd.getDependencyRevisionId.getRevision}"
      // Add the artifacts we know about the module
      packaging match {
        case "pom" =>
          // TODO - Here we have to attempt to download the JAR and see if it comes, if not, we can punt.
          val request = new AetherArtifactRequest()
          request.setArtifact(
            new AetherArtifact(
              s"${dd.getDependencyId.getOrganisation}:${dd.getDependencyId.getName}:jar:${dd.getDependencyRevisionId.getRevision}"))
          try {
            val result = system.resolveArtifact(session, request)
            if (result.isResolved) {
              val defaultArt =
                new DefaultArtifact(md.getModuleRevisionId, new Date, artifactId, packaging, "jar")
              md.addArtifact("master", defaultArt)
            }
          } catch {
            case e: ArtifactResolutionException =>
            // Ignore, as we're just working around issues with people pushing JARs for pom packaging.
          }
        case JarPackaging() =>
          // Assume for now everything else is a jar.
          val defaultArt =
            new DefaultArtifact(md.getModuleRevisionId, new Date, artifactId, packaging, "jar")
          md.addArtifact("master", defaultArt)
        case _ => // Ignore, we have no idea what this artifact is.
      }

    } else for (requestedArt <- dd.getAllDependencyArtifacts) {
      getClassifier(requestedArt) match {
        case null =>
        // For null scope, we don't need to do it.
        case scope =>
          Message.debug(s"Adding additional artifact in $scope, $requestedArt")
          // TODO - Extra attributes?
          val mda =
            new MDArtifact(
              md,
              requestedArt.getName,
              requestedArt.getType,
              requestedArt.getExt,
              requestedArt.getUrl,
              requestedArt.getExtraAttributes)
          md.addArtifact(getConfiguration(scope), mda)
      }
    }
  }

  /** Adds the dependency mediators required based on the managed dependency instances from this pom. */
  def addManagedDependenciesFromAether(result: AetherDescriptorResult, md: DefaultModuleDescriptor) {
    for (d <- result.getManagedDependencies.asScala) {
      // TODO - For each of these append some kind of dependency mediator.
      md.addDependencyDescriptorMediator(
        ModuleId.newInstance(d.getArtifact.getGroupId(), d.getArtifact.getArtifactId()),
        ExactPatternMatcher.INSTANCE,
        new OverrideDependencyDescriptorMediator(null, d.getArtifact.getVersion()))

    }
  }

  /** Adds the list of dependencies this artifact has on other artifacts. */
  def addDependenciesFromAether(result: AetherDescriptorResult, md: DefaultModuleDescriptor) {
    for (d <- result.getDependencies.asScala) {
      // TODO - Is this correct for changing detection
      val isChanging = d.getArtifact.getVersion.endsWith("-SNAPSHOT")
      val drid = ModuleRevisionId.newInstance(d.getArtifact.getGroupId, d.getArtifact.getArtifactId, d.getArtifact.getVersion)
      val dd = new DefaultDependencyDescriptor(md, drid, /* force = */ false, isChanging, true) {}
      // TODO - Configuration mappings (are we grabbing scope correctly, or should the default not always be compile?)
      val scope = Option(d.getScope).filterNot(_.isEmpty).getOrElse("compile")
      val mapping = ReplaceMavenConfigurationMappings.addMappings(dd, scope, d.isOptional)
      // TODO - include rules and exclude rules.
      Message.debug(s"Adding maven transitive dependency ${md.getModuleRevisionId} -> ${dd}")
      // TODO - Unify this borrowed Java code into something a bit friendlier.
      // Now we add the artifact....
      if ((d.getArtifact.getClassifier != null) || ((d.getArtifact.getExtension != null) && !("jar" == d.getArtifact.getExtension))) {
        val tpe: String =
          if (d.getArtifact.getExtension != null) d.getArtifact.getExtension
          else "jar"
        val ext: String = tpe match {
          case "test-jar"     => "jar"
          case JarPackaging() => "jar"
          case other          => other
        }
        //if (dep.getClassifier != null) {
        //extraAtt.put("m:classifier", dep.getClassifier)
        //}
        val depArtifact: DefaultDependencyArtifactDescriptor =
          new DefaultDependencyArtifactDescriptor(dd, dd.getDependencyId.getName, tpe, ext, null, new java.util.HashMap[String, AnyRef]())
        val optionalizedScope: String = if (d.isOptional) "optional" else scope
        // TOOD - We may need to fix the configuration mappings here.
        dd.addDependencyArtifact(optionalizedScope, depArtifact)
      }

      md.addDependency(dd)
    }
  }

  // This method appears to be deprecated/unused in all of Ivy so we do not implement it.
  override def findIvyFileRef(dd: DependencyDescriptor, rd: ResolveData): ResolvedResource = {
    Message.error("Looking for ivy file ref, method not implemented!")
    throw new NoSuchMethodError("findIvyFileRef no longer used in Ivy, and not implemented.")
  }

  private def getPackagingFromPomProperties(props: java.util.Map[String, AnyRef]): String =
    if (props.containsKey(SbtExtraProperties.MAVEN_PACKAGING_KEY))
      props.get(SbtExtraProperties.MAVEN_PACKAGING_KEY).toString
    else "jar"

  override def download(artifacts: Array[Artifact], dopts: DownloadOptions): DownloadReport = {
    // TODO - Status reports on download and possibly parallel downloads
    val report = new DownloadReport
    val requests =
      for (a <- artifacts) yield {
        val request = new AetherArtifactRequest
        val aetherArt =
          getClassifier(a) match {
            case null => new AetherArtifact(aetherCoordsFromMrid(a.getModuleRevisionId))
            case other => new AetherArtifact(
              s"${a.getModuleRevisionId.getOrganisation}:${a.getModuleRevisionId.getName}:${a.getExt}:$other:${a.getModuleRevisionId.getRevision}")
          }
        Message.debug(s"Requesting download of [$aetherArt]")
        request.setArtifact(aetherArt)
        request.addRepository(aetherRepository)
        request
      }
    val (aetherResults, failed) =
      try {
        (system.resolveArtifacts(session, requests.toList.asJava).asScala, false)
      } catch {
        case e: org.eclipse.aether.resolution.ArtifactResolutionException =>
          Message.error(s"Failed to resolve artifacts from ${repo}, ${e.getMessage}")
          (e.getResults.asScala, true)
      }
    for ((result, art) <- aetherResults zip artifacts) {
      Message.debug(s"Aether resolved artifact result: $result")
      val adr = new ArtifactDownloadReport(art)
      adr.setDownloadDetails(result.toString)
      // TODO - Fill this out with a real estimate on time...
      adr.setDownloadTimeMillis(0L)
      // TODO - what is artifact origin actuallyused for?
      adr.setArtifactOrigin(new ArtifactOrigin(
        art,
        true,
        repo.name))
      if (result.isMissing) {
        adr.setDownloadStatus(DownloadStatus.FAILED)
        adr.setDownloadDetails(ArtifactDownloadReport.MISSING_ARTIFACT)
      } else if (!result.isResolved) {
        adr.setDownloadStatus(DownloadStatus.FAILED)
        adr.setDownloadDetails(result.toString)
        // TODO - we should set download status to NO in the event we don't care about an artifact...
      } else {
        val file = result.getArtifact.getFile
        Message.debug(s"Succesffully downloaded: $file")
        adr.setLocalFile(file)
        adr.setSize(file.length)
        adr.setDownloadStatus(DownloadStatus.SUCCESSFUL)
      }
      report.addArtifactReport(adr)
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
      case "doc" | "javadoc" => "javadoc"
      case "src" | "source"  => "sources"
      case _ =>
        // Look for extra attributes
        art.getExtraAttribute(MavenRepositoryResolver.CLASSIFIER_ATTRIBUTE) match {
          case null => null
          case c    => c
        }
    }
  }

  def getClassifier(art: org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor): String =
    art.getType match {
      case "doc" | "javadoc" => "javadoc"
      case "src" | "source"  => "sources"
      case _ =>
        // Look for extra attributes
        art.getExtraAttribute(MavenRepositoryResolver.CLASSIFIER_ATTRIBUTE) match {
          case null => null
          case c    => c
        }
    }

  def getConfiguration(classifier: String): String =
    classifier match {
      // TODO - choice of configuraiton actually depends on whether or not the artifact is
      // REQUESTED by the user, in which case it should be on master.
      // Currently, we don't actually look for sources/javadoc/test artifacts at all,
      // which means any artifact is in the master configuration, but we should
      // fix this for better integration into the maven ecosystem from ivy.
      //case "sources" => "sources"
      //case "javadoc" => "javadoc"
      case other => "master"
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
