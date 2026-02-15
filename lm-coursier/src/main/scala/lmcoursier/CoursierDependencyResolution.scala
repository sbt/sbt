package lmcoursier

import java.io.File
import java.net.{ URI, URLClassLoader }

import coursier.{ Organization, Resolution }
import coursier.core.{ Classifier, Configuration }
import coursier.cache.CacheDefaults
import coursier.util.Artifact
import coursier.internal.Typelevel
import lmcoursier.definitions.ToCoursier
import lmcoursier.internal.{
  ArtifactsParams,
  ArtifactsRun,
  CoursierModuleDescriptor,
  InterProjectRepository,
  LockFile,
  LockedArtifactsRun,
  ResolutionParams,
  ResolutionRun,
  ResolutionSerializer,
  Resolvers,
  SbtBootJars,
  UpdateParams,
  UpdateRun
}
import lmcoursier.syntax.*
import sbt.internal.librarymanagement.IvySbt
import sbt.librarymanagement.*
import sbt.util.Logger
import coursier.core.{ BomDependency, Dependency, Publication }

import scala.util.{ Try, Failure }

class CoursierDependencyResolution(
    conf: CoursierConfiguration,
    protocolHandlerConfiguration: Option[CoursierConfiguration],
    bootstrappingProtocolHandler: Boolean
) extends DependencyResolutionInterface {

  def this(conf: CoursierConfiguration) =
    this(
      conf,
      protocolHandlerConfiguration = None,
      bootstrappingProtocolHandler = true
    )

  private var protocolHandlerClassLoader: Option[ClassLoader] = None
  private val protocolHandlerClassLoaderLock = new Object

  private def fetchProtocolHandlerClassLoader(
      configuration: UpdateConfiguration,
      uwconfig: UnresolvedWarningConfiguration,
      log: Logger
  ): ClassLoader = {

    val conf0 = protocolHandlerConfiguration.getOrElse(conf)

    def isUnknownProtocol(rawURL: String): Boolean = {
      Try(new URI(rawURL).toURL) match {
        case Failure(ex) if ex.getMessage.startsWith("unknown protocol: ") => true
        case _                                                             => false
      }
    }

    val confWithoutUnknownProtocol =
      conf0.withResolvers(
        conf0.resolvers.filter {
          case maven: MavenRepository =>
            !isUnknownProtocol(maven.root)
          case _ =>
            true
        }
      )

    val resolution = new CoursierDependencyResolution(
      conf = confWithoutUnknownProtocol,
      protocolHandlerConfiguration = None,
      bootstrappingProtocolHandler = false
    )

    val fakeModule =
      ModuleDescriptorConfiguration(
        ModuleID("lmcoursier", "lmcoursier", "0.1.0"),
        ModuleInfo("protocol-handler")
      )
        .withDependencies(conf0.protocolHandlerDependencies.toVector)

    val reportOrUnresolved =
      resolution.update(moduleDescriptor(fakeModule), configuration, uwconfig, log)

    val report = reportOrUnresolved match {
      case Right(report0) =>
        report0

      case Left(unresolvedWarning) =>
        import sbt.util.ShowLines.*
        unresolvedWarning.lines.foreach(log.warn(_))
        throw unresolvedWarning.resolveException
    }

    val jars =
      for {
        reportConfiguration <- report.configurations.filter(_.configuration.name == "runtime")
        module <- reportConfiguration.modules
        (_, jar) <- module.artifacts
      } yield jar

    new URLClassLoader(jars.map(_.toURI().toURL()).toArray)
  }

  /*
   * Based on earlier implementations by @leonardehrenfried (https://github.com/sbt/librarymanagement/pull/190)
   * and @andreaTP (https://github.com/sbt/librarymanagement/pull/270), then adapted to the code from the former
   * sbt-coursier, that was moved to this module.
   */

  def moduleDescriptor(moduleSetting: ModuleDescriptorConfiguration): ModuleDescriptor =
    CoursierModuleDescriptor(moduleSetting, conf)

  def update(
      module: ModuleDescriptor,
      configuration: UpdateConfiguration,
      uwconfig: UnresolvedWarningConfiguration,
      log: Logger
  ): Either[UnresolvedWarning, UpdateReport] = {

    if (bootstrappingProtocolHandler && protocolHandlerClassLoader.isEmpty)
      protocolHandlerClassLoaderLock.synchronized {
        if (bootstrappingProtocolHandler && protocolHandlerClassLoader.isEmpty) {
          val classLoader = fetchProtocolHandlerClassLoader(configuration, uwconfig, log)
          protocolHandlerClassLoader = Some(classLoader)
        }
      }

    val conf = this.conf.withUpdateConfiguration(configuration)

    // TODO Take stuff in configuration into account? uwconfig too?

    val module0 = module match {
      case c: CoursierModuleDescriptor =>
        // seems not to happen, not sure what DependencyResolutionInterface.moduleDescriptor is for
        c.descriptor
      case i: IvySbt#Module =>
        i.moduleSettings match {
          case d: ModuleDescriptorConfiguration => d
          case other => sys.error(s"unrecognized module settings: $other")
        }
      case _ =>
        sys.error(s"unrecognized ModuleDescriptor type: $module")
    }

    val so = conf.scalaOrganization
      .map(Organization(_))
      .orElse(module0.scalaModuleInfo.map(m => Organization(m.scalaOrganization)))
      .getOrElse(Organization("org.scala-lang"))
    val sv = conf.scalaVersion
      .orElse(module0.scalaModuleInfo.map(_.scalaFullVersion))
      // FIXME Manage to do stuff below without a scala version?
      .getOrElse(scala.util.Properties.versionNumberString)

    val sbv = module0.scalaModuleInfo.map(_.scalaBinaryVersion).getOrElse {
      sv.split('.').take(2).mkString(".")
    }
    val projectPlatform = module0.scalaModuleInfo.flatMap(_.platform)
    val (mod, ver) = FromSbt.moduleVersion(
      module0.module,
      sv,
      sbv,
      optionalCrossVer = true,
      projectPlatform = projectPlatform
    )
    val interProjectDependencies = {
      val needed = conf.interProjectDependencies.exists { p =>
        p.module == mod && p.version == ver
      }

      if (needed)
        conf.interProjectDependencies.map(ToCoursier.project)
      else
        Vector.empty[coursier.core.Project]
    }

    val extraProjects = conf.extraProjects.map(ToCoursier.project)

    val verbosityLevel = conf.verbosityLevel

    val ttl = conf.ttl
    val loggerOpt = conf.logger.map(ToCoursier.cacheLogger)
    val cache = conf.cache.getOrElse(CacheDefaults.location)
    val cachePolicies = conf.cachePolicies.map(ToCoursier.cachePolicy)
    val checksums = conf.checksums
    val projectName = module0.module.name

    val ivyProperties = ResolutionParams.defaultIvyProperties(conf.ivyHome)

    val classifiers =
      if (conf.hasClassifiers)
        Some(conf.classifiers.map(Classifier(_)))
      else
        None

    val authenticationByRepositoryId = conf.authenticationByRepositoryId.toMap

    val mainRepositories = conf.resolvers
      .flatMap { resolver =>
        Resolvers.repository(
          resolver,
          ivyProperties,
          log,
          authenticationByRepositoryId.get(resolver.name).map(ToCoursier.authentication),
          protocolHandlerClassLoader.toSeq,
        )
      }

    val interProjectRepo = InterProjectRepository(interProjectDependencies)
    val extraProjectsRepo = InterProjectRepository(extraProjects)

    // BOM (Bill of Materials): deps with only pom artifact (e.g. .pomOnly()) go to Resolve.addBom (sbt#4531)
    def isBom(m: ModuleID): Boolean =
      m.explicitArtifacts.nonEmpty && m.explicitArtifacts.forall(_.`type` == "pom")
    val (bomModules, regularModules) = module0.dependencies.partition(isBom)
    val boms: Seq[BomDependency] = bomModules.map { m =>
      val (mod, ver) =
        FromSbt.moduleVersion(
          m,
          sv,
          sbv,
          optionalCrossVer = true,
          projectPlatform = projectPlatform
        )
      BomDependency(ToCoursier.module(mod), ver, Configuration.empty)
    }
    // Coursier fills version from BOM only when versionConstraint is empty (Resolution.processedRootDependencies).
    // So for deps with "*" or "" and BOMs present, pass empty version so BOM can supply it (sbt#4531).
    val dependencies = regularModules
      .flatMap { d =>
        FromSbt.dependencies(d, sv, sbv, optionalCrossVer = true, projectPlatform = projectPlatform)
      }
      .map { (config, dep) =>
        val depForResolve =
          if (boms.nonEmpty && (dep.version == "*" || dep.version.isEmpty))
            lmcoursier.definitions.Dependency(
              dep.module,
              "",
              dep.configuration,
              dep.exclusions,
              dep.publication,
              dep.optional,
              dep.transitive
            )
          else
            dep
        (ToCoursier.configuration(config), ToCoursier.dependency(depForResolve))
      }

    val orderedConfigs = Inputs
      .orderedConfigurations(Inputs.configExtendsSeq(module0.configurations))
      .map { (config, extends0) =>
        (ToCoursier.configuration(config), extends0.map(ToCoursier.configuration))
      }

    val typelevel = so == Typelevel.typelevelOrg

    val cache0 = coursier.cache
      .FileCache()
      .withLocation(cache)
      .withCachePolicies(cachePolicies)
      .withTtl(ttl)
      .withChecksums(checksums)
      .withCredentials(conf.credentials.map(ToCoursier.credentials))
      .withFollowHttpToHttpsRedirections(conf.followHttpToHttpsRedirections.getOrElse(true))
      .withLocalArtifactsShouldBeCached(conf.localArtifactsShouldBeCached)

    val excludeDependencies = conf.excludeDependencies.map { (strOrg, strName) =>
      (coursier.Organization(strOrg), coursier.ModuleName(strName))
    }.toSet

    val resolutionParams = ResolutionParams(
      dependencies = dependencies,
      fallbackDependencies = conf.fallbackDependencies,
      orderedConfigs = orderedConfigs,
      autoScalaLibOpt = if (conf.autoScalaLibrary) Some((so, sv)) else None,
      mainRepositories = mainRepositories,
      parentProjectCache = Map.empty,
      interProjectDependencies = interProjectDependencies,
      internalRepositories = Seq(interProjectRepo, extraProjectsRepo),
      sbtClassifiers = conf.sbtClassifiers,
      projectName = projectName,
      loggerOpt = loggerOpt,
      cache = cache0,
      parallel = conf.parallelDownloads,
      params = coursier.params
        .ResolutionParams()
        .withMaxIterations(conf.maxIterations)
        .withProfiles(conf.mavenProfiles.toSet)
        .withForceVersion(conf.forceVersions.map { (k, v) => (ToCoursier.module(k), v) }.toMap)
        .withTypelevel(typelevel)
        .withReconciliation(ToCoursier.reconciliation(conf.reconciliation))
        .withExclusions(excludeDependencies)
        .withRules(ToCoursier.sameVersions(conf.sameVersions)),
      strictOpt = conf.strict.map(ToCoursier.strict),
      missingOk = conf.missingOk,
      retry = conf.retry.getOrElse(ResolutionParams.defaultRetry),
      boms = boms,
    )

    def artifactsParams(resolutions: Map[Configuration, Resolution]): ArtifactsParams =
      ArtifactsParams(
        classifiers = classifiers,
        resolutions = resolutions.values.toSeq.distinct,
        includeSignatures = false,
        loggerOpt = loggerOpt,
        projectName = projectName,
        sbtClassifiers = conf.sbtClassifiers,
        cache = cache0,
        parallel = conf.parallelDownloads,
        classpathOrder = conf.classpathOrder,
        missingOk = conf.missingOk
      )

    val sbtBootJarOverrides = SbtBootJars(
      conf.sbtScalaOrganization.fold(Organization("org.scala-lang"))(Organization(_)),
      conf.sbtScalaVersion.getOrElse(sv),
      conf.sbtScalaJars
    )

    val configs = Inputs.coursierConfigurationsMap(module0.configurations).map { (k, l) =>
      ToCoursier.configuration(k) -> l.map(ToCoursier.configuration)
    }

    def updateParams(
        resolutions: Map[Configuration, Resolution],
        artifacts: Seq[(Dependency, Publication, Artifact, Option[File])]
    ) =
      UpdateParams(
        thisModule = (ToCoursier.module(mod), ver),
        artifacts = artifacts.collect { case (d, p, a, Some(f)) => a -> f }.toMap,
        fullArtifacts = Some(artifacts.map { (d, p, a, f) => (d, p, a) -> f }.toMap),
        classifiers = classifiers,
        configs = configs,
        dependencies = dependencies,
        forceVersions = conf.forceVersions.map { (m, v) => (ToCoursier.module(m), v) }.toMap,
        interProjectDependencies = interProjectDependencies,
        res = resolutions,
        includeSignatures = false,
        sbtBootJarOverrides = sbtBootJarOverrides,
        classpathOrder = conf.classpathOrder,
        missingOk = conf.missingOk,
        classLoaders = protocolHandlerClassLoader.toSeq,
      )

    val e = for {
      (resolutions, lockDataOpt) <- ResolutionRun.resolutionsWithLockFileData(
        resolutionParams,
        verbosityLevel,
        log,
        conf.lockFile,
        conf.scalaVersion
      )
      artifactResult <- lockDataOpt match {
        case Some(lockData) =>
          LockedArtifactsRun.fetchFromLockFile(lockData, cache0, verbosityLevel, log) match {
            case Right(arts) => Right(arts)
            case Left(err) =>
              if (verbosityLevel >= 1) {
                log.warn(s"Failed to fetch from lock file: $err, falling back to normal fetch")
              }
              ArtifactsRun(artifactsParams(resolutions), verbosityLevel, log)
                .map(_.fullDetailedArtifacts)
          }
        case None =>
          ArtifactsRun(artifactsParams(resolutions), verbosityLevel, log)
            .map(_.fullDetailedArtifacts)
      }
    } yield {
      val updateParams0 = updateParams(resolutions, artifactResult)
      val report = UpdateRun.update(updateParams0, verbosityLevel, log)
      if (lockDataOpt.isEmpty) {
        conf.lockFile.foreach { lockFile =>
          val artifactMap = artifactResult
            .groupBy(_._1)
            .view
            .mapValues(_.map { case (_, pub, art, _) =>
              val originalUrl =
                lmcoursier.internal.CacheUrlConversion.cacheFileToOriginalUrl(art.url, cache)
              (originalUrl, pub.classifier.value, pub.ext.value)
            })
            .toMap
          val lockData = ResolutionSerializer.extractLockFileData(
            resolutions,
            resolutionParams,
            conf.scalaVersion,
            "2.0.0",
            artifactMap
          )
          LockFile.write(lockFile, lockData)
        }
      }
      report
    }
    e.left.map(unresolvedWarningOrThrow(uwconfig, _))
  }

  private def unresolvedWarningOrThrow(
      uwconfig: UnresolvedWarningConfiguration,
      ex: coursier.error.CoursierError
  ): UnresolvedWarning = {

    // TODO Take coursier.error.FetchError.DownloadingArtifacts into account

    val downloadErrors = ex match {
      case ex0: coursier.error.ResolutionError =>
        ex0.errors.collect { case err: coursier.error.ResolutionError.CantDownloadModule =>
          err
        }
      case _ =>
        Nil
    }
    val otherErrors = ex match {
      case ex0: coursier.error.ResolutionError =>
        ex0.errors.flatMap {
          case _: coursier.error.ResolutionError.CantDownloadModule => None
          case err                                                  => Some(err)
        }
      case _ =>
        Seq(ex)
    }

    if (otherErrors.isEmpty) {
      val r = new ResolveException(
        downloadErrors.map(_.getMessage),
        downloadErrors.map { err =>
          ModuleID(err.module.organization.value, err.module.name.value, err.version)
            .withExtraAttributes(err.module.attributes)
        }
      )
      UnresolvedWarning(r, uwconfig)
    } else
      throw ex
  }
}

object CoursierDependencyResolution {
  def apply(configuration: CoursierConfiguration): DependencyResolution =
    DependencyResolution(new CoursierDependencyResolution(configuration))

  def apply(
      configuration: CoursierConfiguration,
      protocolHandlerConfiguration: Option[CoursierConfiguration]
  ): DependencyResolution =
    DependencyResolution(
      new CoursierDependencyResolution(
        configuration,
        protocolHandlerConfiguration,
        bootstrappingProtocolHandler = true
      )
    )

  def defaultCacheLocation: File =
    CacheDefaults.location

  private[lmcoursier] def cacheFileToOriginalUrl(fileUrl: String, cacheDir: File): String =
    lmcoursier.internal.CacheUrlConversion.cacheFileToOriginalUrl(fileUrl, cacheDir)
}
