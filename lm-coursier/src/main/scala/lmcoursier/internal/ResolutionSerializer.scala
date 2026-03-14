package lmcoursier.internal

import coursier.{ Project, Resolution }
import coursier.core.{ ArtifactSource, Configuration, Dependency, Info, Module }
import coursier.error.DependencyError
import coursier.version.VersionConstraint
import scala.annotation.nowarn
import scala.collection.immutable.Seq

object ResolutionSerializer {

  def extractLockFileData(
      resolutions: Map[Configuration, Resolution],
      params: ResolutionParams,
      scalaVersion: Option[String],
      sbtVersion: String,
      artifactMap: Map[Dependency, Seq[(String, String, String)]]
  ): Either[DependencyError, LockFileData] =
    val buildClock = BuildClock.compute(
      params.dependencies,
      params.mainRepositories,
      scalaVersion,
      params
    )

    val configurations0 = resolutions.toVector
      .sortBy(_._1.value)
      .map { case (config, resolution) =>
        extractDependencies(resolution, config, artifactMap) match
          case Right(dependencies) => Right(ConfigurationLock(config.value, dependencies.toVector))
          case Left(err)           => Left(err)
      }
    traverseEither(configurations0).map: configurations =>
      val metadata = LockFileMetadata(
        sbtVersion = sbtVersion,
        scalaVersion = scalaVersion
      )

      LockFileData(
        version = LockFileConstants.currentVersion,
        buildClock = buildClock,
        configurations = configurations,
        metadata = metadata
      )

  private def extractDependencies(
      resolution: Resolution,
      config: Configuration,
      artifactMap: Map[Dependency, Seq[(String, String, String)]]
  ): Either[DependencyError, Vector[DependencyLock]] =
    val dependencies = resolution.minDependencies
    val xs = dependencies.toVector
      .sortBy(d => (d.module.toString, d.versionConstraint.asString))
      .map: dep =>
        val resolvedVersion: String = resolution.retainedVersions
          .get(dep.module) match
          case Some(v) => s"$v"
          case None    => s"${dep.versionConstraint.asString}"

        resolution
          .dependenciesOf0(dep, withRetainedVersions = true)
          .map: transitives0 =>
            val transitives = transitives0
              .map: d =>
                s"${d.module.organization.value}:${d.module.name.value}:${d.versionConstraint.asString}"
              .sorted

            val artifacts =
              artifactMap.getOrElse(dep, Seq.empty).map { case (url, classifier, ext) =>
                ArtifactLock(
                  url = url,
                  classifier = if (classifier.isEmpty) None else Some(classifier),
                  extension = ext,
                  tpe = dep.attributes.`type`.value
                )
              }

            DependencyLock(
              organization = dep.module.organization.value,
              name = dep.module.name.value,
              version = resolvedVersion,
              configuration = dep.variantSelector.repr,
              classifier = dep.attributes.classifier.value match {
                case "" => None
                case c  => Some(c)
              },
              tpe = dep.attributes.`type`.value,
              transitives = transitives.toVector,
              artifacts = artifacts.toVector
            )
    traverseEither(xs)

  private def traverseEither[A1, A2](xs: Vector[Either[A1, A2]]): Either[A1, Vector[A2]] =
    xs.foldLeft(Right(Vector.empty): Either[A1, Vector[A2]]) {
      case (Left(acc), x)         => Left(acc)
      case (Right(acc), Left(x))  => Left(x)
      case (Right(acc), Right(x)) => Right(acc ++ Vector(x))
    }

  def reconstructResolutions(
      lockFileData: LockFileData,
      params: ResolutionParams
  ): Map[Configuration, Resolution] = {
    lockFileData.configurations.map { configLock =>
      val config = Configuration(configLock.name)
      val resolution = reconstructResolution(configLock, params)
      config -> resolution
    }.toMap
  }

  private def reconstructResolution(
      configLock: ConfigurationLock,
      params: ResolutionParams
  ): Resolution = {
    val forceVersions: Map[Module, String] = configLock.dependencies.map { depLock =>
      val module = Module(
        coursier.Organization(depLock.organization),
        coursier.ModuleName(depLock.name),
        Map.empty[String, String]
      )
      module -> depLock.version
    }.toMap

    val rootDeps = params.dependencies
      .filter(_._1.value == configLock.name)
      .map(_._2)

    val dependencies: Set[Dependency] = configLock.dependencies.map { depLock =>
      Dependency(
        Module(
          coursier.Organization(depLock.organization),
          coursier.ModuleName(depLock.name),
          Map.empty[String, String]
        ),
        VersionConstraint(depLock.version),
      )
    }.toSet

    val projectCache: Map[(Module, String), (ArtifactSource, Project)] =
      configLock.dependencies.map { depLock =>
        val module = Module(
          coursier.Organization(depLock.organization),
          coursier.ModuleName(depLock.name),
          Map.empty[String, String]
        )
        val project = (Project(
          module = module,
          version = depLock.version,
          dependencies = Seq.empty,
          configurations = Map.empty,
          parent = None,
          dependencyManagement = Seq.empty,
          properties = Seq.empty,
          profiles = Seq.empty,
          versions = None,
          snapshotVersioning = None,
          packagingOpt = None,
          relocated = false,
          actualVersionOpt = None,
          publications = Seq.empty,
          info = Info.empty
        ): @nowarn)
        (module, depLock.version) -> (EmptyArtifactSource, project)
      }.toMap

    (Resolution()
      .withRootDependencies(rootDeps)
      .withDependencies(dependencies)
      .withForceVersions(forceVersions ++ params.params.forceVersion)
      .withProjectCache(projectCache): @nowarn)
  }

  private object EmptyArtifactSource extends ArtifactSource {
    def artifacts(
        dependency: Dependency,
        project: Project,
        overrideClassifiers: Option[scala.collection.immutable.Seq[coursier.core.Classifier]]
    ): scala.collection.immutable.Seq[(coursier.core.Publication, coursier.util.Artifact)] =
      scala.collection.immutable.Seq.empty
  }

  def getLockedArtifacts(
      lockFileData: LockFileData
  ): Map[(String, String, String), Seq[ArtifactLock]] = {
    lockFileData.configurations.flatMap { configLock =>
      configLock.dependencies.map { depLock =>
        (depLock.organization, depLock.name, depLock.version) -> depLock.artifacts
      }
    }.toMap
  }
}
