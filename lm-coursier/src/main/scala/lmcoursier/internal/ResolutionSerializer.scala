package lmcoursier.internal

import coursier.core.{ Configuration, Module, Resolution }
import java.time.Instant
import scala.collection.immutable.Seq

object ResolutionSerializer {

  def extractLockFileData(
      resolutions: Map[Configuration, Resolution],
      params: ResolutionParams,
      scalaVersion: Option[String],
      sbtVersion: String
  ): LockFileData = {
    val buildClock = BuildClock.compute(
      params.dependencies,
      params.mainRepositories,
      scalaVersion,
      params
    )

    val configurations = resolutions.toSeq.sortBy(_._1.value).map { case (config, resolution) =>
      val dependencies = extractDependencies(resolution, config)
      ConfigurationLock(config.value, dependencies)
    }

    val metadata = LockFileMetadata(
      sbtVersion = sbtVersion,
      scalaVersion = scalaVersion,
      timestamp = Instant.now()
    )

    LockFileData(
      version = LockFileData.currentVersion,
      buildClock = buildClock,
      configurations = configurations,
      metadata = metadata
    )
  }

  private def extractDependencies(
      resolution: Resolution,
      config: Configuration
  ): Seq[DependencyLock] = {
    val dependencies = resolution.minDependencies

    dependencies.toSeq.sortBy(d => (d.module.toString, d.version)).map { dep =>
      val resolvedVersion: String = resolution.retainedVersions
        .get(dep.module) match {
        case Some(v) => s"$v"
        case None    => s"${dep.version}"
      }

      val transitives = resolution
        .dependenciesOf(dep, withRetainedVersions = true)
        .map(d => s"${d.module.organization.value}:${d.module.name.value}:${d.version}")
        .sorted

      DependencyLock(
        organization = dep.module.organization.value,
        name = dep.module.name.value,
        version = resolvedVersion,
        configuration = dep.configuration.value,
        classifier = dep.attributes.classifier.value match {
          case "" => None
          case c  => Some(c)
        },
        `type` = dep.attributes.`type`.value,
        transitives = transitives
      )
    }
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

    Resolution()
      .withRootDependencies(rootDeps)
      .withForceVersions(forceVersions ++ params.params.forceVersion)
  }
}
