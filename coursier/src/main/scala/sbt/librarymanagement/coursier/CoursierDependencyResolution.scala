package sbt.librarymanagement.coursier

import java.io.{ File, OutputStreamWriter }
import java.util.concurrent.Executors

import scala.util.{ Failure, Success }
import scala.concurrent.ExecutionContext
import coursier.{ Artifact, Resolution, _ }
import coursier.util.{ Gather, Task }
import sbt.internal.librarymanagement.IvySbt
import sbt.librarymanagement.Configurations.{ CompilerPlugin, Component, ScalaTool }
import sbt.librarymanagement._
import sbt.util.Logger
import sjsonnew.JsonFormat
import sjsonnew.support.murmurhash.Hasher

case class CoursierModuleDescriptor(
    directDependencies: Vector[ModuleID],
    scalaModuleInfo: Option[ScalaModuleInfo],
    moduleSettings: ModuleSettings,
    extraInputHash: Long,
    configurations: Seq[String]
) extends ModuleDescriptor

case class CoursierModuleSettings() extends ModuleSettings

private[sbt] class CoursierDependencyResolution(coursierConfiguration: CoursierConfiguration)
    extends DependencyResolutionInterface {

  // keep the pool alive while the class is loaded
  private lazy val pool =
    ExecutionContext.fromExecutor(
      Executors.newFixedThreadPool(coursierConfiguration.parallelDownloads)
    )

  private[coursier] val reorderedResolvers = {
    val resolvers0 =
      coursierConfiguration.resolvers ++ coursierConfiguration.otherResolvers

    if (coursierConfiguration.reorderResolvers) {
      Resolvers.reorder(resolvers0)
    } else resolvers0
  }

  private[sbt] object AltLibraryManagementCodec extends CoursierLibraryManagementCodec {
    type CoursierHL = (
        Vector[Resolver],
        Vector[Resolver],
        Boolean,
        Int,
        Int
    )

    def coursierToHL(c: CoursierConfiguration): CoursierHL =
      (
        c.resolvers,
        c.otherResolvers,
        c.reorderResolvers,
        c.parallelDownloads,
        c.maxIterations
      )
    // Redefine to use a subset of properties, that are serialisable
    override implicit lazy val CoursierConfigurationFormat: JsonFormat[CoursierConfiguration] = {
      def hlToCoursier(c: CoursierHL): CoursierConfiguration = {
        val (
          resolvers,
          otherResolvers,
          reorderResolvers,
          parallelDownloads,
          maxIterations
        ) = c
        CoursierConfiguration()
          .withResolvers(resolvers)
          .withOtherResolvers(otherResolvers)
          .withReorderResolvers(reorderResolvers)
          .withParallelDownloads(parallelDownloads)
          .withMaxIterations(maxIterations)
      }
      projectFormat[CoursierConfiguration, CoursierHL](coursierToHL, hlToCoursier)
    }
  }

  def extraInputHash: Long = {
    import AltLibraryManagementCodec._
    Hasher.hash(coursierConfiguration) match {
      case Success(keyHash) => keyHash.toLong
      case Failure(_)       => 0L
    }
  }

  /**
   * Builds a ModuleDescriptor that describes a subproject with dependencies.
   *
   * @param moduleSetting It contains the information about the module including the dependencies.
   * @return A `ModuleDescriptor` describing a subproject and its dependencies.
   */
  override def moduleDescriptor(
      moduleSetting: ModuleDescriptorConfiguration): CoursierModuleDescriptor = {
    CoursierModuleDescriptor(
      moduleSetting.dependencies,
      moduleSetting.scalaModuleInfo,
      CoursierModuleSettings(),
      extraInputHash,
      moduleSetting.configurations.map(_.name)
    )
  }

  val ivyHome = sys.props.getOrElse(
    "ivy.home",
    new File(sys.props("user.home")).toURI.getPath + ".ivy2"
  )

  val sbtIvyHome = sys.props.getOrElse(
    "sbt.ivy.home",
    ivyHome
  )

  val ivyProperties = Map(
    "ivy.home" -> ivyHome,
    "sbt.ivy.home" -> sbtIvyHome
  ) ++ sys.props

  /**
   * Resolves the given module's dependencies performing a retrieval.
   *
   * @param module        The module to be resolved.
   * @param configuration The update configuration.
   * @param uwconfig      The configuration to handle unresolved warnings.
   * @param log           The logger.
   * @return The result, either an unresolved warning or an update report. Note that this
   *         update report will or will not be successful depending on the `missingOk` option.
   */
  override def update(module: ModuleDescriptor,
                      configuration: UpdateConfiguration,
                      uwconfig: UnresolvedWarningConfiguration,
                      log: Logger): Either[UnresolvedWarning, UpdateReport] = {

    // not sure what DependencyResolutionInterface.moduleDescriptor is for, we're handled ivy stuff anyway...
    val module0 = module match {
      case c: CoursierModuleDescriptor => c
      case i: IvySbt#Module =>
        moduleDescriptor(
          i.moduleSettings match {
            case c: ModuleDescriptorConfiguration => c
            case other                            => sys.error(s"unrecognized module settings: $other")
          }
        )
      case _ =>
        sys.error(s"unrecognized ModuleDescriptor type: $module")
    }

    if (reorderedResolvers.isEmpty) {
      log.error(
        "Dependency resolution is configured with an empty list of resolvers. This is unlikely to work.")
    }

    val dependencies = module.directDependencies.map(toCoursierDependency).flatten.toSet
    val start = Resolution(dependencies)
    val authentication = None // TODO: get correct value
    val ivyConfiguration = ivyProperties // TODO: is it enough?

    val repositories =
      reorderedResolvers.flatMap(r => FromSbt.repository(r, ivyConfiguration, log, authentication)) ++ Seq(
        Cache.ivy2Local,
        Cache.ivy2Cache
      )

    implicit val ec = pool

    val coursierLogger = createLogger()
    try {
      val fetch = Fetch.from(
        repositories,
        Cache.fetch[Task](logger = Some(coursierLogger))
      )
      val resolution = start.process
        .run(fetch, coursierConfiguration.maxIterations)
        .unsafeRun()

      def updateReport() = {
        val localArtifacts: Map[Artifact, Either[FileError, File]] = Gather[Task]
          .gather(
            resolution.artifacts.map { a =>
              Cache
                .file[Task](a, logger = Some(coursierLogger))
                .run
                .map((a, _))
            }
          )
          .unsafeRun()
          .toMap

        toUpdateReport(resolution, module0.configurations, localArtifacts, log)
      }

      if (resolution.isDone &&
          resolution.errors.isEmpty &&
          resolution.conflicts.isEmpty) {
        updateReport()
      } else if (resolution.isDone &&
                 (!resolution.errors.isEmpty && coursierConfiguration.ignoreArtifactErrors)
                 && resolution.conflicts.isEmpty) {
        log.warn(s"""Failed to download artifacts: ${resolution.errors
          .map(_._2)
          .flatten
          .mkString(", ")}""")
        updateReport()
      } else {
        toSbtError(log, uwconfig, resolution)
      }
    } finally {
      coursierLogger.stop()
    }
  }

  // utilities
  private def createLogger() = {
    val t = new TermDisplay(new OutputStreamWriter(System.out))
    t.init()
    t
  }

  private def toCoursierDependency(moduleID: ModuleID): Seq[Dependency] = {
    val attributes =
      if (moduleID.explicitArtifacts.isEmpty)
        Seq(Attributes("", ""))
      else
        moduleID.explicitArtifacts.map { a =>
          Attributes(`type` = a.`type`, classifier = a.classifier.getOrElse(""))
        }

    val extraAttrs = FromSbt.attributes(moduleID.extraDependencyAttributes)

    val mapping = moduleID.configurations.getOrElse("compile")

    // import _root_.coursier.ivy.IvyXml.{ mappings => ivyXmlMappings }
    // val allMappings = ivyXmlMappings(mapping)
    for {
      attr <- attributes
    } yield {
      Dependency(
        Module(moduleID.organization, moduleID.name, extraAttrs),
        moduleID.revision,
        configuration = mapping,
        attributes = attr,
        exclusions = moduleID.exclusions.map { rule =>
          (rule.organization, rule.name)
        }.toSet,
        transitive = moduleID.isTransitive
      )
    }
  }

  private def toUpdateReport(resolution: Resolution,
                             configurations: Seq[String],
                             artifactFilesOrErrors0: Map[Artifact, Either[FileError, File]],
                             log: Logger): Either[UnresolvedWarning, UpdateReport] = {

    val artifactFiles = artifactFilesOrErrors0.collect {
      case (artifact, Right(file)) =>
        artifact -> file
    }

    val artifactErrors = artifactFilesOrErrors0.toVector
      .collect {
        case (a, Left(err)) if !a.isOptional || !err.notFound =>
          a -> err
      }

    val erroredArtifacts = artifactFilesOrErrors0.collect {
      case (a, Left(_)) => a
    }.toSet

    val depsByConfig = {
      val deps = resolution.dependencies.toVector
      configurations.map((_, deps)).toMap
    }

    val configurations0 = extractConfigurationTree

    val configResolutions =
      (depsByConfig.keys ++ configurations0.keys).map(k => (k, resolution)).toMap

    val sbtBootJarOverrides = Map.empty[(Module, String), File] // TODO: get correct values
    val classifiers = None // TODO: get correct values

    if (artifactErrors.isEmpty) {
      Right(
        ToSbt.updateReport(
          depsByConfig,
          configResolutions,
          configurations0,
          classifiers,
          artifactFileOpt(
            sbtBootJarOverrides,
            artifactFiles,
            erroredArtifacts,
            log,
            _,
            _,
            _
          ),
          log
        ))
    } else {
      throw new RuntimeException(s"Could not save downloaded dependencies: $erroredArtifacts")
    }

  }

  type ConfigurationName = String
  type ConfigurationDependencyTree = Map[ConfigurationName, Set[ConfigurationName]]

  // Key is the name of the configuration (i.e. `compile`) and the values are the name itself plus the
  // names of the configurations that this one depends on.
  private def extractConfigurationTree: ConfigurationDependencyTree = {
    (Configurations.default ++
      Configurations.defaultInternal ++
      Seq(ScalaTool, CompilerPlugin, Component))
      .map(c => (c.name, c.extendsConfigs.map(_.name) :+ c.name))
      .toMap
      .mapValues(_.toSet)
  }

  private def artifactFileOpt(
      sbtBootJarOverrides: Map[(Module, String), File],
      artifactFiles: Map[Artifact, File],
      erroredArtifacts: Set[Artifact],
      log: Logger,
      module: Module,
      version: String,
      artifact: Artifact
  ) = {

    val artifact0 = artifact
      .copy(attributes = Attributes()) // temporary hack :-(

    // Under some conditions, SBT puts the scala JARs of its own classpath
    // in the application classpath. Ensuring we return SBT's jars rather than
    // JARs from the coursier cache, so that a same JAR doesn't land twice in the
    // application classpath (once via SBT jars, once via coursier cache).
    val fromBootJars =
      if (artifact.classifier.isEmpty && artifact.`type` == "jar")
        sbtBootJarOverrides.get((module, version))
      else
        None

    val res = fromBootJars.orElse(artifactFiles.get(artifact0))
    if (res.isEmpty && !erroredArtifacts(artifact0))
      log.error(s"${artifact.url} not downloaded (should not happen)")

    res
  }

  private def toSbtError(log: Logger,
                         uwconfig: UnresolvedWarningConfiguration,
                         resolution: Resolution) = {
    val failedResolution = resolution.errors.map {
      case ((failedModule, failedVersion), _) =>
        ModuleID(failedModule.organization, failedModule.name, failedVersion)
    }
    val msgs = resolution.errors.flatMap(_._2)
    log.debug(s"Failed resolution: $msgs")
    log.debug(s"Missing artifacts: $failedResolution")
    val ex = new ResolveException(msgs, failedResolution)
    Left(UnresolvedWarning(ex, uwconfig))
  }
}

object CoursierDependencyResolution {
  def apply(configuration: CoursierConfiguration) =
    DependencyResolution(new CoursierDependencyResolution(configuration))
}
