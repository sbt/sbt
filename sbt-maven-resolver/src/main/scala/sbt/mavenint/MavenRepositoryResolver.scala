package sbt.mavenint

import java.io.File
import java.util.Date

import org.apache.ivy.core.IvyContext
import org.apache.ivy.core.cache.{ ArtifactOrigin, ModuleDescriptorWriter }
import org.apache.ivy.core.module.descriptor._
import org.apache.ivy.core.module.id.{ ArtifactId, ModuleId, ModuleRevisionId }
import org.apache.ivy.core.report.{
  ArtifactDownloadReport,
  DownloadReport,
  DownloadStatus,
  MetadataArtifactDownloadReport
}
import org.apache.ivy.core.resolve.{ DownloadOptions, ResolveData, ResolvedModuleRevision }
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.matcher.{ ExactPatternMatcher, PatternMatcher }
import org.apache.ivy.plugins.parser.m2.{ PomModuleDescriptorBuilder, ReplaceMavenConfigurationMappings }
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorWriter
import org.apache.ivy.plugins.resolver.AbstractResolver
import org.apache.ivy.plugins.resolver.util.ResolvedResource
import org.apache.ivy.util.Message
import org.eclipse.aether.artifact.{ DefaultArtifact => AetherArtifact }
import org.eclipse.aether.deployment.{ DeployRequest => AetherDeployRequest }
import org.eclipse.aether.installation.{ InstallRequest => AetherInstallRequest }
import org.eclipse.aether.metadata.{ DefaultMetadata, Metadata }
import org.eclipse.aether.resolution.{
  ArtifactDescriptorRequest => AetherDescriptorRequest,
  ArtifactDescriptorResult => AetherDescriptorResult,
  ArtifactRequest => AetherArtifactRequest,
  ArtifactResolutionException,
  MetadataRequest => AetherMetadataRequest,
  VersionRequest => AetherVersionRequest,
  VersionRangeRequest => AetherVersionRangeRequest
}
import org.eclipse.aether.{ RepositorySystem, RepositorySystemSession }
import sbt.ivyint.{ CustomMavenResolver, CustomRemoteMavenResolver }
import sbt.mavenint.MavenRepositoryResolver.JarPackaging
import sbt.{ MavenCache, MavenRepository }

import scala.collection.JavaConverters._

object MavenRepositoryResolver {
  val MAVEN_METADATA_XML = "maven-metadata.xml"
  val CLASSIFIER_ATTRIBUTE = "e:classifier"
  // TODO - This may be duplciated in more than one location.  We need to consolidate.
  val JarPackagings = Set("eclipse-plugin", "hk2-jar", "orbit", "scala-jar", "jar", "bundle")
  object JarPackaging {
    def unapply(in: String): Boolean = JarPackagings.contains(in)
  }
  // Example: 2014 12 18  09 33 56
  val LAST_UPDATE_FORMAT = new java.text.SimpleDateFormat("yyyyMMddhhmmss")
  def parseTimeString(in: String): Option[Long] =
    try Some(LAST_UPDATE_FORMAT.parse(in).getTime)
    catch {
      case _: java.text.ParseException => None
    }
  val DEFAULT_ARTIFACT_CONFIGURATION = "master"
}

/**
  * An abstract repository resolver which has the basic hooks for mapping from Maven (Aether) notions into Ivy notions.
  *
  * THis is used to implement local-cache resolution from ~/.m2 caches or resolving from remote repositories.
  */
abstract class MavenRepositoryResolver(settings: IvySettings) extends AbstractResolver {

  /** Our instance of the aether repository system. */
  protected val system: RepositorySystem

  /**
    * Our instance of the aether repository system session.
    *
    * TODO - We may want to tie this into an IvyContext.
    */
  protected val session: RepositorySystemSession

  /** Determine the publication time of a module.  The mechanism may differ if the repository is remote vs. local. */
  protected def getPublicationTime(mrid: ModuleRevisionId): Option[Long]

  /** Inject necessary repositories into a descriptor request. */
  protected def addRepositories(request: AetherDescriptorRequest): AetherDescriptorRequest
  protected def addRepositories(request: AetherArtifactRequest): AetherArtifactRequest
  protected def addRepositories(request: AetherVersionRequest): AetherVersionRequest
  protected def addRepositories(request: AetherVersionRangeRequest): AetherVersionRangeRequest

  /** Actually publishes aether artifacts. */
  protected def publishArtifacts(artifacts: Seq[AetherArtifact]): Unit

  // TOOD - deal with packaging here.
  private def aetherArtifactIdFromMrid(mrid: ModuleRevisionId): String =
    getSbtVersion(mrid) match {
      case Some(sbt) => s"${mrid.getName}_sbt_$sbt"
      case None      => mrid.getName
    }
  private def aetherCoordsFromMrid(mrid: ModuleRevisionId): String =
    s"${mrid.getOrganisation}:${aetherArtifactIdFromMrid(mrid)}:${mrid.getRevision}"

  private def aetherCoordsFromMrid(mrid: ModuleRevisionId, packaging: String): String =
    s"${mrid.getOrganisation}:${aetherArtifactIdFromMrid(mrid)}:$packaging:${mrid.getRevision}"

  private def aetherCoordsFromMrid(mrid: ModuleRevisionId, packaging: String, extension: String): String =
    s"${mrid.getOrganisation}:${aetherArtifactIdFromMrid(mrid)}:$extension:$packaging:${mrid.getRevision}"

  // Handles appending licenses to the module descriptor fromthe pom.
  private def addLicenseInfo(md: DefaultModuleDescriptor, map: java.util.Map[String, AnyRef]) = {
    val count = map.get(SbtPomExtraProperties.LICENSE_COUNT_KEY) match {
      case null                 => 0
      case x: java.lang.Integer => x.intValue
      case x: String            => x.toInt
      case _                    => 0
    }
    for {
      i <- 0 until count
      name <- Option(map.get(SbtPomExtraProperties.makeLicenseName(i))).map(_.toString)
      url <- Option(map.get(SbtPomExtraProperties.makeLicenseUrl(i))).map(_.toString)
    } md.addLicense(new License(name, url))
  }

  // This grabs the dependency for Ivy.
  override def getDependency(dd: DependencyDescriptor, rd: ResolveData): ResolvedModuleRevision = {
    val context = IvyContext.pushNewCopyContext
    try {
      val drid: ModuleRevisionId =
        if (sbt.MakePom.isDependencyVersionRange(dd.getDependencyRevisionId.getRevision)) {
          Message.debug(
            s"Got a dynamic revision, attempting to convert to real revision: ${dd.getDependencyRevisionId}"
          )
          val revision = sbt.MakePom.makeDependencyVersion(dd.getDependencyRevisionId.getRevision)
          // TODO - Alter revision id to be maven-friendly first.
          val coords =
            s"${dd.getDependencyRevisionId.getOrganisation}:${aetherArtifactIdFromMrid(dd.getDependencyRevisionId)}:${revision}"
          //val coords = aetherCoordsFromMrid(dd.getDependencyRevisionId)
          Message.debug(s"Aether about to resolve version for [$coords]...")
          val versionRequest = addRepositories(
            new AetherVersionRangeRequest()
              .setArtifact(new AetherArtifact(coords, getArtifactProperties(dd.getDependencyRevisionId)))
          )
          val result = system.resolveVersionRange(session, versionRequest)
          Message.debug(s"Version result = $result, from $getName")
          if (result.getVersions.isEmpty)
            throw new MavenResolutionException(s"Did not find any versions for $dd")
          ModuleRevisionId.newInstance(
            dd.getDependencyRevisionId.getOrganisation,
            dd.getDependencyRevisionId.getName,
            result.getHighestVersion.toString,
            dd.getExtraAttributes
          )
        } else dd.getDependencyRevisionId

      // TODO - Check to see if we're asking for latest.* version, and if so, we should run a latest version query
      //        first and use that result to return the metadata/final module.
      Message.debug(
        s"Requesting conf [${dd.getModuleConfigurations.mkString(",")}] from Aether module ${drid} in resolver ${getName}"
      )
      val request = new AetherDescriptorRequest()
      val coords = aetherCoordsFromMrid(drid)
      Message.debug(s"Aether about to resolve [$coords]...")
      request.setArtifact(new AetherArtifact(coords, getArtifactProperties(drid)))
      addRepositories(request)
      val result = system.readArtifactDescriptor(session, request)
      val packaging = getPackagingFromPomProperties(result.getProperties)
      Message.debug(s"Aether resolved ${dd.getDependencyId} w/ packaging ${packaging}")

      // TODO - better pub date if we have no metadata.
      val lastModifiedTime = getPublicationTime(drid) getOrElse 0L

      // Construct a new Ivy module descriptor
      val desc: ModuleDescriptor = {
        // TODO - Better detection of snapshot and handling latest.integration/latest.snapshot
        val status =
          if (drid.getRevision.endsWith("-SNAPSHOT")) "integration"
          else "release"
        val md =
          new DefaultModuleDescriptor(drid, status, null /* pubDate */, false)
        //DefaultModuleDescriptor.newDefaultInstance(dd.getDependencyRevisionId)
        // Here we add the standard configurations
        for (config <- PomModuleDescriptorBuilder.MAVEN2_CONFIGURATIONS) {
          md.addConfiguration(config)
        }

        // Here we look into the artifacts specified from the dependency descriptor *and* those that are defaulted,
        // and append them to the appropriate configurations.
        // TODO - Feed correct revision down here
        addArtifactsFromPom(drid, dd, packaging, md, lastModifiedTime)
        // Here we add dependencies.
        addDependenciesFromAether(result, md)
        // Here we use pom.xml Dependency management section to create Ivy dependency mediators.
        addManagedDependenciesFromAether(result, md)

        // Here we rip out license info.
        addLicenseInfo(md, result.getProperties)
        md.addExtraInfo(SbtPomExtraProperties.MAVEN_PACKAGING_KEY, packaging)
        Message.debug(s"Setting publication date to ${new Date(lastModifiedTime)}")
        // TODO - Figure out the differences between these items.
        md.setPublicationDate(new Date(lastModifiedTime))
        md.setLastModified(lastModifiedTime)
        md.setResolvedPublicationDate(new Date(lastModifiedTime))
        md.check()
        // TODO - do we need the toSystem?
        toSystem(md)
      }

      // Here we need to pretend we downloaded the pom.xml file
      val pom = DefaultArtifact.newPomArtifact(drid, new java.util.Date(lastModifiedTime))
      val madr = new MetadataArtifactDownloadReport(pom)
      madr.setSearched(true)
      madr.setDownloadStatus(DownloadStatus.SUCCESSFUL) // TODO - Figure this things out for this report.
      val rmr = new ResolvedModuleRevision(this, this, desc, madr, false /* Force */ )

      // TODO - Here we cache the transformed pom.xml into an ivy.xml in the cache because ChainResolver will be looking at it.
      //        This doesn't appear to really work correctly.
      //        However, I think the chain resolver doesn't use this instance anyway.  Ideally we don't put anything
      //        in the ivy cache, but this should be "ok".
      getRepositoryCacheManager.originalToCachedModuleDescriptor(
        this,
        null /* ivyRef.  Just passed back to us. */,
        pom,
        rmr,
        new ModuleDescriptorWriter() {
          def write(originalMdResource: ResolvedResource, md: ModuleDescriptor, src: File, dest: File): Unit =
            // a basic ivy file is written containing default data
            XmlModuleDescriptorWriter.write(md, dest);
        }
      )
      rmr
    } catch {
      case e: org.eclipse.aether.resolution.ArtifactDescriptorException =>
        Message.info(s"Failed to read descriptor ${dd} from ${getName}, ${e.getMessage}")
        rd.getCurrentResolvedModuleRevision
      case e: MavenResolutionException =>
        Message.debug(
          s"Resolution Exception from ${getName}, ${e.getMessage}, returning: ${rd.getCurrentResolvedModuleRevision}"
        )
        rd.getCurrentResolvedModuleRevision
    } finally IvyContext.popContext()
  }

  def getSbtVersion(dd: ModuleRevisionId): Option[String] =
    Option(dd.getExtraAttribute(PomExtraDependencyAttributes.SbtVersionKey))

  def getArtifactProperties(dd: ModuleRevisionId): java.util.Map[String, String] = {
    val m = new java.util.HashMap[String, String]
    Option(dd.getExtraAttribute(PomExtraDependencyAttributes.ScalaVersionKey)) foreach { sv =>
      m.put(SbtPomExtraProperties.POM_SCALA_VERSION, sv)
    }
    getSbtVersion(dd) foreach { sv =>
      m.put(SbtPomExtraProperties.POM_SBT_VERSION, sv)
    }
    m
  }

  final def checkJarArtifactExists(dd: ModuleRevisionId): Boolean = {
    // TODO - We really want this to be as fast/efficient as possible!
    val request = new AetherArtifactRequest()
    val art = new AetherArtifact(aetherCoordsFromMrid(dd, "jar"), getArtifactProperties(dd))
    request.setArtifact(art)
    addRepositories(request)
    try {
      val result = system.resolveArtifact(session, request)
      result.isResolved && !result.isMissing
    } catch {
      case e: ArtifactResolutionException =>
        // Ignore, as we're just working around issues with pom.xml's with no jars or POM packaging
        Message.debug(s"Could not find $art in ${getName}")
        false
    }
  }

  /** Determines which artifacts are associated with this maven module and appends them to the descriptor. */
  def addArtifactsFromPom(drid: ModuleRevisionId,
                          dd: DependencyDescriptor,
                          packaging: String,
                          md: DefaultModuleDescriptor,
                          lastModifiedTime: Long): Unit = {
    Message.debug(s"Calculating artifacts for ${dd.getDependencyId} w/ packaging $packaging")
    // Here we add in additional artifact requests, which ALLWAYS have to be explicit since
    // Maven/Aether doesn't include all known artifacts in a pom.xml
    // TODO - This does not appear to be working correctly.
    if (dd.getAllDependencyArtifacts.isEmpty) {
      val artifactId = s"${drid.getName}-${drid.getRevision}"
      // Add the artifacts we know about the module
      packaging match {
        case "pom" =>
          // THere we have to attempt to download the JAR and see if it comes, if not, we can punt.
          // This is because sometimes pom-packaging attaches a JAR.
          if (checkJarArtifactExists(drid)) {
            val defaultArt =
              new DefaultArtifact(
                md.getModuleRevisionId,
                new Date(lastModifiedTime),
                artifactId,
                packaging,
                "jar"
              )
            md.addArtifact(MavenRepositoryResolver.DEFAULT_ARTIFACT_CONFIGURATION, defaultArt)
          }
        case JarPackaging() =>
          // Here we fail the resolution.  This is an issue when pom.xml files exist with no JAR, which happens
          // on maven central for some reason on old artifacts.
          if (!checkJarArtifactExists(drid))
            throw new MavenResolutionException(s"Failed to find JAR file associated with $dd")
          // Assume for now everything else is a jar.
          val defaultArt =
            new DefaultArtifact(
              md.getModuleRevisionId,
              new Date(lastModifiedTime),
              artifactId,
              packaging,
              "jar"
            )
          // TODO - Unfortunately we have to try to download the JAR file HERE and then fail resolution if we cannot find it.
          //       This is because sometime a pom.xml exists with no JARs.
          md.addArtifact(MavenRepositoryResolver.DEFAULT_ARTIFACT_CONFIGURATION, defaultArt)
        case _ => // Ignore, we have no idea what this artifact is.
          Message.warn(
            s"Not adding artifacts for resolution because we don't understand packaging: $packaging"
          )
      }

    } else {
      // NOTE:  this means that someone is requested specific artifacts from us.   What we need to do is *only* download the
      //        requested artifacts rather than the default "jar".    What's odd, is that pretty much this almost ALWAYS happens.
      //        but in some circumstances, the above logic is checked.
      //        Additionally, we may want to somehow merge the "defined" artifacts from maven with the requested ones here, rather
      //       than having completely separate logic.   For now, this appears to work the same way it was before.
      //        Since we aren't accurately guessing what maven files are meant to be included as artifacts ANYWAY, this
      //        is probably the right way to go.
      for (requestedArt <- dd.getAllDependencyArtifacts) {
        getClassifier(requestedArt) match {
          case None =>
            // This is the default artifact.  We do need to add this, and to the default configuration.
            val defaultArt =
              new DefaultArtifact(
                md.getModuleRevisionId,
                new Date(lastModifiedTime),
                requestedArt.getName,
                requestedArt.getType,
                requestedArt.getExt
              )
            md.addArtifact(MavenRepositoryResolver.DEFAULT_ARTIFACT_CONFIGURATION, defaultArt)
          case Some(scope) =>
            Message.debug(s"Adding additional artifact in $scope, $requestedArt")
            // TODO - more Extra attributes?
            val mda =
              new MDArtifact(
                md,
                requestedArt.getName,
                requestedArt.getType,
                requestedArt.getExt,
                requestedArt.getUrl,
                requestedArt.getExtraAttributes
              )
            md.addArtifact(getConfiguration(scope), mda)
        }
      }
    }
  }

  /** Adds the dependency mediators required based on the managed dependency instances from this pom. */
  def addManagedDependenciesFromAether(result: AetherDescriptorResult, md: DefaultModuleDescriptor): Unit =
    for (d <- result.getManagedDependencies.asScala) {
      // TODO - Figure out what to do about exclusions on managed dependencies.
      md.addDependencyDescriptorMediator(
        ModuleId.newInstance(d.getArtifact.getGroupId, d.getArtifact.getArtifactId),
        ExactPatternMatcher.INSTANCE,
        new OverrideDependencyDescriptorMediator(null, d.getArtifact.getVersion) {
          override def mediate(dd: DependencyDescriptor): DependencyDescriptor =
            super.mediate(dd)
        }
      )

    }

  /** Adds the list of dependencies this artifact has on other artifacts. */
  def addDependenciesFromAether(result: AetherDescriptorResult, md: DefaultModuleDescriptor): Unit = {
    // First we construct a map of any extra attributes we must append to dependencies.
    // This is necessary for transitive maven-based sbt plugin dependencies, where we need to
    // attach the sbtVersion/scalaVersion to the dependency id otherwise we'll fail to resolve the
    // dependency correctly.
    val extraAttributes = PomExtraDependencyAttributes.readFromAether(result.getProperties)
    for (d <- result.getDependencies.asScala) {
      // TODO - Is this correct for changing detection.  We should use the Ivy mechanism configured...
      val isChanging = d.getArtifact.getVersion.endsWith("-SNAPSHOT")
      val drid = {
        val tmp = ModuleRevisionId.newInstance(
          d.getArtifact.getGroupId,
          d.getArtifact.getArtifactId,
          d.getArtifact.getVersion
        )
        extraAttributes get tmp match {
          case Some(props) =>
            Message.debug(s"Found $tmp w/ extra attributes ${props.mkString(",")}")
            ModuleRevisionId.newInstance(
              d.getArtifact.getGroupId,
              d.getArtifact.getArtifactId,
              d.getArtifact.getVersion,
              props.asJava
            )
          case _ => tmp
        }
      }
      val transitive = d.getExclusions.asScala.forall(e => e.getGroupId != "*" || e.getArtifactId != "*")

      // Note: The previous maven integration ALWAYS set force to true for dependnecies.  If we do not do this, for some
      //       reason, Ivy will create dummy nodes when doing dependnecy mediation (e.g. dependencyManagement of one pom overrides version of a dependency)
      //       which was leading to "data not found" exceptions as Ivy would pick the correct IvyNode in the dependency tree but never load it with data....
      val dd = new DefaultDependencyDescriptor(md, drid, /* force  */ true, isChanging, transitive) {}

      // TODO - Configuration mappings (are we grabbing scope correctly, or should the default not always be compile?)
      val scope = Option(d.getScope).filterNot(_.isEmpty).getOrElse("compile")
      val mapping = ReplaceMavenConfigurationMappings.addMappings(dd, scope, d.isOptional)
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
        // Here we add the classifier, hopefully correctly...
        val extraAtt = new java.util.HashMap[String, AnyRef]()
        if (d.getArtifact.getClassifier != null) {
          extraAtt.put("m:classifier", d.getArtifact.getClassifier)
        }
        val depArtifact: DefaultDependencyArtifactDescriptor =
          new DefaultDependencyArtifactDescriptor(dd, dd.getDependencyId.getName, tpe, ext, null, extraAtt)
        val optionalizedScope: String = if (d.isOptional) "optional" else scope
        // TOOD - We may need to fix the configuration mappings here.
        dd.addDependencyArtifact(optionalizedScope, depArtifact)
      }
      if (dd.isTransitive) {
        // Include rules and exclude rules.
        for (e <- d.getExclusions.asScala) {
          val excludedModule = new ModuleId(e.getGroupId, e.getArtifactId)
          for (conf <- dd.getModuleConfigurations) {
            // TODO - Do we need extra attributes for this?
            dd.addExcludeRule(
              conf,
              new DefaultExcludeRule(
                new ArtifactId(
                  excludedModule,
                  PatternMatcher.ANY_EXPRESSION,
                  PatternMatcher.ANY_EXPRESSION,
                  PatternMatcher.ANY_EXPRESSION
                ),
                ExactPatternMatcher.INSTANCE,
                null
              )
            )
          }
        }
      }
      md.addDependency(dd)
    }
  }

  // This method appears to be deprecated/unused in all of Ivy so we do not implement it.
  override def findIvyFileRef(dd: DependencyDescriptor, rd: ResolveData): ResolvedResource = {
    Message.error(
      s"Looking for ivy file ref, method not implemented!  MavenRepositoryResolver($getName) will always return null."
    )
    null
  }

  private def getPackagingFromPomProperties(props: java.util.Map[String, AnyRef]): String =
    if (props.containsKey(SbtPomExtraProperties.MAVEN_PACKAGING_KEY))
      props.get(SbtPomExtraProperties.MAVEN_PACKAGING_KEY).toString
    else "jar"

  override def download(artifacts: Array[Artifact], dopts: DownloadOptions): DownloadReport = {
    // TODO - Status reports on download and possibly parallel downloads
    val report = new DownloadReport
    val requests =
      for (a <- artifacts) yield {
        val request = new AetherArtifactRequest
        val aetherArt =
          getClassifier(a) match {
            case None | Some("") =>
              new AetherArtifact(
                aetherCoordsFromMrid(a.getModuleRevisionId),
                getArtifactProperties(a.getModuleRevisionId)
              )
            case Some(other) =>
              new AetherArtifact(
                aetherCoordsFromMrid(a.getModuleRevisionId, other, a.getExt),
                getArtifactProperties(a.getModuleRevisionId)
              )
          }
        Message.debug(s"Requesting download of [$aetherArt]")
        request.setArtifact(aetherArt)
        addRepositories(request)
        request
      }
    val (aetherResults, failed) =
      try {
        (system.resolveArtifacts(session, requests.toList.asJava).asScala, false)
      } catch {
        case e: org.eclipse.aether.resolution.ArtifactResolutionException =>
          Message.error(s"Failed to resolve artifacts from ${getName}, ${e.getMessage}")
          (e.getResults.asScala, true)
      }
    for ((result, art) <- aetherResults zip artifacts) {
      Message.debug(s"Aether resolved artifact result: $result")
      val adr = new ArtifactDownloadReport(art)
      adr.setDownloadDetails(result.toString)
      // TODO - Fill this out with a real estimate on time...
      adr.setDownloadTimeMillis(0L)
      // TODO - what is artifact origin actuallyused for?
      adr.setArtifactOrigin(new ArtifactOrigin(art, true, getName))
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

  override def beginPublishTransaction(module: ModuleRevisionId, overwrite: Boolean): Unit =
    currentTransaction match {
      case Some(t) => throw new IllegalStateException(s"Publish Transaction already open for [$getName]")
      case None    => currentTransaction = Some(PublishTransaction(module, Nil))
    }
  override def abortPublishTransaction(): Unit =
    currentTransaction = None

  def getClassifier(art: Artifact): Option[String] =
    // TODO - Do we need to look anywere else?
    Option(art.getExtraAttribute("classifier"))

  def getClassifier(art: org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor): Option[String] =
    art.getType match {
      case "doc" | "javadoc"   => Some("javadoc")
      case "src" | "source"    => Some("sources")
      case "test-jar" | "test" => Some("tests")
      case _                   =>
        // Look for extra attributes
        art.getExtraAttribute(MavenRepositoryResolver.CLASSIFIER_ATTRIBUTE) match {
          case null => None
          case c    => Some(c)
        }
    }

  def getConfiguration(classifier: String): String =
    classifier match {
      // TODO - choice of configuration actually depends on whether or not the artifact is
      // REQUESTED by the user, in which case it should be on master.
      // Currently, we don't actually look for sources/javadoc/test artifacts at all,
      // which means any artifact is in the master configuration, but we should
      // fix this for better integration into the maven ecosystem from ivy.
      //case "sources" => "sources"
      //case "javadoc" => "javadoc"
      case other => MavenRepositoryResolver.DEFAULT_ARTIFACT_CONFIGURATION
    }

  override def commitPublishTransaction(): Unit =
    // TODO - actually send all artifacts to aether
    currentTransaction match {
      case Some(t) =>
        Message.debug(s"Publishing module ${t.module}, with artifact count = ${t.artifacts.size}")
        val artifacts =
          for ((art, file) <- t.artifacts) yield {
            Message.debug(s" - Publishing $art (${art.getType})(${art
              .getExtraAttribute("classifier")}) in [${art.getConfigurations.mkString(",")}] from $file")
            new AetherArtifact(
              t.module.getOrganisation,
              aetherArtifactIdFromMrid(t.module),
              getClassifier(art).orNull,
              art.getExt,
              t.module.getRevision,
              getArtifactProperties(t.module),
              file
            )
          }
        publishArtifacts(artifacts)
        // TODO - Any kind of validity checking?
        currentTransaction = None
      case None => throw new IllegalStateException(s"Publish Transaction already open for [$getName]")
    }

  override def publish(art: Artifact, file: File, overwrite: Boolean): Unit =
    currentTransaction match {
      case Some(t) =>
        val allArts = t.artifacts ++ List(art -> file)
        currentTransaction = Some(t.copy(artifacts = allArts))
      case None =>
        throw new IllegalStateException(("MavenRepositories require transactional publish"))
    }

  override def equals(a: Any): Boolean =
    a match {
      case x: MavenRepositoryResolver => x.getName == getName
      case _                          => false
    }
  override def hashCode: Int = getName.hashCode
}
