package sbt.librarymanagement.coursier

import java.io.{ File, OutputStreamWriter }

import coursier.{ Artifact, Resolution, _ }
import coursier.util.{ Gather, Task }
import sbt.librarymanagement.Configurations.{ CompilerPlugin, Component, ScalaTool }
import sbt.librarymanagement._
import sbt.util.Logger

case class CoursierModuleDescriptor(
    directDependencies: Vector[ModuleID],
    scalaModuleInfo: Option[ScalaModuleInfo],
    moduleSettings: ModuleSettings,
    extraInputHash: Long
) extends ModuleDescriptor

case class CoursierModuleSettings() extends ModuleSettings

private[sbt] class CoursierDependencyResolution(resolvers: Seq[Resolver])
    extends DependencyResolutionInterface {

  private[coursier] val reorderedResolvers = Resolvers.reorder(resolvers)

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
      1L // FIXME: use correct value
    )
  }

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

    if (reorderedResolvers.isEmpty) {
      log.error(
        "Dependency resolution is configured with an empty list of resolvers. This is unlikely to work.")
    }

    val dependencies = module.directDependencies.map(toCoursierDependency).toSet
    val start = Resolution(dependencies)
    val authentication = None // TODO: get correct value
    val ivyConfiguration = Map("ivy.home" -> "~/.ivy2/") // TODO: get correct value
    val repositories =
      reorderedResolvers.flatMap(r => FromSbt.repository(r, ivyConfiguration, log, authentication)) ++ Seq(
        Cache.ivy2Local,
        Cache.ivy2Cache)

    import scala.concurrent.ExecutionContext.Implicits.global

    val fetch = Fetch.from(repositories, Cache.fetch[Task](logger = Some(createLogger())))
    val resolution = start.process.run(fetch).unsafeRun()

    if (resolution.errors.isEmpty) {
      val localArtifacts: Map[Artifact, Either[FileError, File]] = Gather[Task]
        .gather(
          resolution.artifacts.map { a =>
            Cache.file[Task](a).run.map((a, _))
          }
        )
        .unsafeRun()
        .toMap
      toUpdateReport(resolution, localArtifacts, log)
    } else {
      toSbtError(log, uwconfig, resolution)
    }
  }

  // utilities

  private def createLogger() = {
    val t = new TermDisplay(new OutputStreamWriter(System.out))
    t.init()
    t
  }

  private def toCoursierDependency(moduleID: ModuleID): Dependency = {
    val attrs = moduleID.explicitArtifacts
      .map(a => Attributes(`type` = a.`type`, classifier = a.classifier.getOrElse("")))
      .headOption
      .getOrElse(Attributes())

    // for some reason, sbt adds the prefix "e:" to extraAttributes
    val extraAttrs = moduleID.extraAttributes.map {
      case (key, value) => (key.replaceFirst("^e:", ""), value)
    }

    Dependency(
      Module(moduleID.organization, moduleID.name, extraAttrs),
      moduleID.revision,
      moduleID.configurations.getOrElse(""),
      attrs,
      exclusions = moduleID.exclusions.map { rule =>
        (rule.organization, rule.name)
      }.toSet,
      transitive = moduleID.isTransitive
    )
  }

  private def toUpdateReport(resolution: Resolution,
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

    if (artifactErrors.nonEmpty) {
      // TODO: handle error the correct sbt way
      throw new RuntimeException(s"Could not download dependencies: $artifactErrors")
    }

    // can be non empty only if ignoreArtifactErrors is true or some optional artifacts are not found
    val erroredArtifacts = artifactFilesOrErrors0.collect {
      case (a, Left(_)) => a
    }.toSet

    val depsByConfig = resolution.dependencies.groupBy(_.configuration).mapValues(_.toSeq)

    val configurations = extractConfigurationTree

    val configResolutions =
      (depsByConfig.keys ++ configurations.keys).map(k => (k, resolution)).toMap

    val sbtBootJarOverrides = Map.empty[(Module, String), File] // TODO: get correct values
    val classifiers = None // TODO: get correct values

    if (artifactErrors.isEmpty) {
      Right(
        ToSbt.updateReport(
          depsByConfig,
          configResolutions,
          configurations,
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
    (Configurations.default ++ Configurations.defaultInternal ++ Seq(ScalaTool,
                                                                     CompilerPlugin,
                                                                     Component))
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
  def apply(resolvers: Seq[Resolver]) =
    DependencyResolution(new CoursierDependencyResolution(resolvers))
}
