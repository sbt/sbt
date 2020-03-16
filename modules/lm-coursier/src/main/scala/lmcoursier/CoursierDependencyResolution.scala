package lmcoursier

import java.io.File

import coursier.{Organization, Resolution, organizationString}
import coursier.core.{Classifier, Configuration}
import coursier.cache.{CacheDefaults, CachePolicy}
import coursier.util.Artifact
import coursier.internal.Typelevel
import lmcoursier.definitions.ToCoursier
import lmcoursier.internal.{ArtifactsParams, ArtifactsRun, CoursierModuleDescriptor, InterProjectRepository, ResolutionParams, ResolutionRun, Resolvers, SbtBootJars, UpdateParams, UpdateRun}
import sbt.internal.librarymanagement.IvySbt
import sbt.librarymanagement._
import sbt.util.Logger

class CoursierDependencyResolution(conf: CoursierConfiguration) extends DependencyResolutionInterface {

  /*
   * Based on earlier implementations by @leonardehrenfried (https://github.com/sbt/librarymanagement/pull/190)
   * and @andreaTP (https://github.com/sbt/librarymanagement/pull/270), then adapted to the code from the former
   * sbt-coursier, that was moved to this module.
   */

  private lazy val excludeDependencies = conf
    .excludeDependencies
    .map {
      case (strOrg, strName) =>
        (lmcoursier.definitions.Organization(strOrg), lmcoursier.definitions.ModuleName(strName))
    }
    .toSet

  def moduleDescriptor(moduleSetting: ModuleDescriptorConfiguration): ModuleDescriptor =
    CoursierModuleDescriptor(moduleSetting, conf)

  def update(
    module: ModuleDescriptor,
    configuration: UpdateConfiguration,
    uwconfig: UnresolvedWarningConfiguration,
    log: Logger
  ): Either[UnresolvedWarning, UpdateReport] = {

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

    val so = conf.scalaOrganization.map(Organization(_))
      .orElse(module0.scalaModuleInfo.map(m => Organization(m.scalaOrganization)))
      .getOrElse(org"org.scala-lang")
    val sv = conf.scalaVersion
      .orElse(module0.scalaModuleInfo.map(_.scalaFullVersion))
      // FIXME Manage to do stuff below without a scala version?
      .getOrElse(scala.util.Properties.versionNumberString)

    val sbv = module0.scalaModuleInfo.map(_.scalaBinaryVersion).getOrElse {
      sv.split('.').take(2).mkString(".")
    }
    val (mod, ver) = FromSbt.moduleVersion(module0.module, sv, sbv, optionalCrossVer = true)
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
    val projectName = "" // used for logging only…

    val ivyProperties = ResolutionParams.defaultIvyProperties(conf.ivyHome)

    val classifiers =
      if (conf.hasClassifiers)
        Some(conf.classifiers.map(Classifier(_)))
      else
        None

    val authenticationByRepositoryId = conf.authenticationByRepositoryId.toMap

    val mainRepositories = conf
      .resolvers
      .flatMap { resolver =>
        Resolvers.repository(
          resolver,
          ivyProperties,
          log,
          authenticationByRepositoryId.get(resolver.name).map(ToCoursier.authentication)
        )
      }

    val interProjectRepo = InterProjectRepository(interProjectDependencies)
    val extraProjectsRepo = InterProjectRepository(extraProjects)

    val dependencies = module0
      .dependencies
      .flatMap { d =>
        // crossVersion sometimes already taken into account (when called via the update task), sometimes not
        // (e.g. sbt-dotty 0.13.0-RC1)
        FromSbt.dependencies(d, sv, sbv, optionalCrossVer = true)
      }
      .map {
        case (config, dep) =>
          val dep0 = dep.withExclusions(dep.exclusions ++ excludeDependencies)
          (ToCoursier.configuration(config), ToCoursier.dependency(dep0))
      }

    val configGraphs = Inputs.ivyGraphs(
      Inputs.configExtends(module0.configurations)
    ).map(_.map(ToCoursier.configuration))

    val typelevel = so == Typelevel.typelevelOrg

    val cache0 = coursier.cache.FileCache()
      .withLocation(cache)
      .withCachePolicies(cachePolicies)
      .withTtl(ttl)
      .withChecksums(checksums)
      .withCredentials(conf.credentials.map(ToCoursier.credentials))
      .withFollowHttpToHttpsRedirections(conf.followHttpToHttpsRedirections.getOrElse(true))

    val resolutionParams = ResolutionParams(
      dependencies = dependencies,
      fallbackDependencies = conf.fallbackDependencies,
      configGraphs = configGraphs,
      autoScalaLibOpt = if (conf.autoScalaLibrary) Some((so, sv)) else None,
      mainRepositories = mainRepositories,
      parentProjectCache = Map.empty,
      interProjectDependencies = interProjectDependencies,
      internalRepositories = Seq(interProjectRepo, extraProjectsRepo),
      sbtClassifiers = false,
      projectName = projectName,
      loggerOpt = loggerOpt,
      cache = cache0,
      parallel = conf.parallelDownloads,
      params = coursier.params.ResolutionParams()
        .withMaxIterations(conf.maxIterations)
        .withProfiles(conf.mavenProfiles.toSet)
        .withForceVersion(conf.forceVersions.map { case (k, v) => (ToCoursier.module(k), v) }.toMap)
        .withTypelevel(typelevel)
        .withReconciliation(ToCoursier.reconciliation(conf.reconciliation)),
      strictOpt = conf.strict.map(ToCoursier.strict)
    )

    def artifactsParams(resolutions: Map[Set[Configuration], Resolution]): ArtifactsParams =
      ArtifactsParams(
        classifiers = classifiers,
        resolutions = resolutions.values.toSeq,
        includeSignatures = false,
        loggerOpt = loggerOpt,
        projectName = projectName,
        sbtClassifiers = false,
        cache = cache0,
        parallel = conf.parallelDownloads,
        classpathOrder = conf.classpathOrder,
      )

    val sbtBootJarOverrides = SbtBootJars(
      conf.sbtScalaOrganization.fold(org"org.scala-lang")(Organization(_)),
      conf.sbtScalaVersion.getOrElse(sv),
      conf.sbtScalaJars
    )

    val configs = Inputs.coursierConfigurations(module0.configurations).map {
      case (k, l) =>
        ToCoursier.configuration(k) -> l.map(ToCoursier.configuration)
    }

    def updateParams(
      resolutions: Map[Set[Configuration], Resolution],
      artifacts: Map[Artifact, File]
    ) =
      UpdateParams(
        thisModule = (ToCoursier.module(mod), ver),
        shadedConfigOpt = None,
        artifacts = artifacts,
        classifiers = classifiers,
        configs = configs,
        dependencies = dependencies,
        interProjectDependencies = interProjectDependencies,
        res = resolutions,
        includeSignatures = false,
        sbtBootJarOverrides = sbtBootJarOverrides,
        classpathOrder = conf.classpathOrder,
      )

    val e = for {
      resolutions <- ResolutionRun.resolutions(resolutionParams, verbosityLevel, log)
      artifactsParams0 = artifactsParams(resolutions)
      artifacts <- ArtifactsRun.artifacts(artifactsParams0, verbosityLevel, log)
    } yield {
      val updateParams0 = updateParams(resolutions, artifacts)
      UpdateRun.update(updateParams0, verbosityLevel, log)
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
        ex0.errors.collect {
          case err: coursier.error.ResolutionError.CantDownloadModule => err
        }
      case _ =>
        Nil
    }
    val otherErrors = ex match {
      case ex0: coursier.error.ResolutionError =>
        ex0.errors.flatMap {
          case _: coursier.error.ResolutionError.CantDownloadModule => None
          case err => Some(err)
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

  def defaultCacheLocation: File =
    CacheDefaults.location
}
