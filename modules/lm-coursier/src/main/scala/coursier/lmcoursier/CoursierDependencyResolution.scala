package coursier.lmcoursier

import java.io.{File, OutputStreamWriter}

import _root_.coursier.{Artifact, Cache, CachePolicy, FileError, Organization, Resolution, TermDisplay, organizationString}
import _root_.coursier.core.{Configuration, ModuleName}
import _root_.coursier.ivy.IvyRepository
import sbt.internal.librarymanagement.IvySbt
import sbt.librarymanagement._
import sbt.util.Logger

class CoursierDependencyResolution(conf: CoursierConfiguration) extends DependencyResolutionInterface {

  private def sbtBinaryVersion = "1.0"

  lazy val resolvers =
    if (conf.reorderResolvers)
      ResolutionParams.reorderResolvers(conf.resolvers)
    else
      conf.resolvers

  private lazy val excludeDependencies = conf
    .excludeDependencies
    .map {
      case (strOrg, strName) =>
        (Organization(strOrg), ModuleName(strName))
    }
    .toSet

  def moduleDescriptor(moduleSetting: ModuleDescriptorConfiguration): CoursierModuleDescriptor =
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

    val so = module0.scalaModuleInfo.fold(org"org.scala-lang")(m => Organization(m.scalaOrganization))
    val sv = module0.scalaModuleInfo.map(_.scalaFullVersion)
      // FIXME Manage to do stuff below without a scala version?
      .getOrElse(scala.util.Properties.versionNumberString)

    val sbv = module0.scalaModuleInfo.map(_.scalaBinaryVersion).getOrElse {
      sv.split('.').take(2).mkString(".")
    }

    val verbosityLevel = 0

    val ttl = Cache.defaultTtl
    val createLogger = { () =>
      new TermDisplay(new OutputStreamWriter(System.err), fallbackMode = true)
    }
    val cache = Cache.default
    val cachePolicies = CachePolicy.default
    val checksums = Cache.defaultChecksums
    val projectName = "" // used for logging only…

    val ivyProperties = ResolutionParams.defaultIvyProperties()

    val mainRepositories = resolvers
      .flatMap { resolver =>
        FromSbt.repository(
          resolver,
          ivyProperties,
          log,
          None // FIXME What about authentication?
        )
      }

    val globalPluginsRepos =
      for (p <- ResolutionParams.globalPluginPatterns(sbtBinaryVersion))
        yield IvyRepository.fromPattern(
          p,
          withChecksums = false,
          withSignatures = false,
          withArtifacts = false
        )

    val interProjectRepo = InterProjectRepository(conf.interProjectDependencies)

    val internalRepositories = globalPluginsRepos :+ interProjectRepo

    val dependencies = module0
      .dependencies
      .flatMap { d =>
        // crossVersion already taken into account, wiping it here
        val d0 = d.withCrossVersion(CrossVersion.Disabled())
        FromSbt.dependencies(d0, sv, sbv)
      }
      .map {
        case (config, dep) =>
          val dep0 = dep.copy(
            exclusions = dep.exclusions ++ excludeDependencies
          )
          (config, dep0)
      }

    val configGraphs = Inputs.ivyGraphs(
      Inputs.configExtends(module0.configurations)
    )

    val resolutionParams = ResolutionParams(
      dependencies = dependencies,
      fallbackDependencies = conf.fallbackDependencies,
      configGraphs = configGraphs,
      autoScalaLib = conf.autoScalaLibrary,
      mainRepositories = mainRepositories,
      parentProjectCache = Map.empty,
      interProjectDependencies = conf.interProjectDependencies,
      internalRepositories = internalRepositories,
      userEnabledProfiles = Set.empty,
      userForceVersions = Map.empty,
      typelevel = false,
      so = so,
      sv = sv,
      sbtClassifiers = false,
      parallelDownloads = conf.parallelDownloads,
      projectName = projectName,
      maxIterations = conf.maxIterations,
      createLogger = createLogger,
      cache = cache,
      cachePolicies = cachePolicies,
      ttl = ttl,
      checksums = checksums
    )

    def artifactsParams(resolutions: Map[Set[Configuration], Resolution]) =
      ArtifactsParams(
        classifiers = None,
        res = resolutions.values.toSeq,
        includeSignatures = false,
        parallelDownloads = conf.parallelDownloads,
        createLogger = createLogger,
        cache = cache,
        artifactsChecksums = checksums,
        ttl = ttl,
        cachePolicies = cachePolicies,
        projectName = projectName,
        sbtClassifiers = false
      )

    val sbtBootJarOverrides = SbtBootJars(
      conf.sbtScalaOrganization.fold(org"org.scala-lang")(Organization(_)),
      conf.sbtScalaVersion.getOrElse(sv),
      conf.sbtScalaJars
    )

    val configs = Inputs.coursierConfigurations(module0.configurations)

    def updateParams(
      resolutions: Map[Set[Configuration], Resolution],
      artifacts: Map[Artifact, Either[FileError, File]]
    ) =
      UpdateParams(
        shadedConfigOpt = None,
        artifacts = artifacts,
        classifiers = None,
        configs = configs,
        dependencies = dependencies,
        res = resolutions,
        ignoreArtifactErrors = false,
        includeSignatures = false,
        sbtBootJarOverrides = sbtBootJarOverrides
      )

    val e = for {
      resolutions <- ResolutionRun.resolutions(resolutionParams, verbosityLevel, log)
      artifactsParams0 = artifactsParams(resolutions)
      artifacts <- ArtifactsRun.artifacts(artifactsParams0, verbosityLevel, log)
      updateParams0 = updateParams(resolutions, artifacts)
      updateReport <- UpdateRun.update(updateParams0, verbosityLevel, log)
    } yield updateReport

    e.left.map(unresolvedWarningOrThrow(uwconfig, _))
  }

  private def resolutionException(ex: ResolutionError): Either[Throwable, ResolveException] =
    ex match {
      case e: ResolutionError.MetadataDownloadErrors =>
        val r = new ResolveException(
          e.errors.flatMap(_._2),
          e.errors.map {
            case ((mod, ver), _) =>
              ModuleID(mod.organization.value, mod.name.value, ver)
                .withExtraAttributes(mod.attributes)
          }
        )
        Right(r)
      case _ => Left(ex.exception())
    }

  private def unresolvedWarningOrThrow(
    uwconfig: UnresolvedWarningConfiguration,
    ex: ResolutionError
  ): UnresolvedWarning =
    resolutionException(ex) match {
      case Left(t) => throw t
      case Right(e) =>
        UnresolvedWarning(e, uwconfig)
    }

}

object CoursierDependencyResolution {
  def apply(configuration: CoursierConfiguration): DependencyResolution =
    DependencyResolution(new CoursierDependencyResolution(configuration))
}
