package lmcoursier.internal

import coursier.core.Resolution.ModuleVersion
import coursier.core._
import coursier.util.Print
import sbt.librarymanagement.UpdateReport
import sbt.util.Logger

// private[coursier]
object UpdateRun {

  // Move back to coursier.util (in core module) after 1.0?
  private def allDependenciesByConfig(
    res: Map[Configuration, Resolution],
    depsByConfig: Map[Configuration, Seq[Dependency]],
    configs: Map[Configuration, Set[Configuration]]
  ): Map[Configuration, Set[Dependency]] = {

    val allDepsByConfig = depsByConfig.map {
      case (config, deps) =>
        config -> res(config).subset(deps).minDependencies
    }

    val filteredAllDepsByConfig = allDepsByConfig.map {
      case (config, allDeps) =>
        val allExtendedConfigs = configs.getOrElse(config, Set.empty) - config
        val inherited = allExtendedConfigs
          .flatMap(allDepsByConfig.getOrElse(_, Set.empty))

        config -> (allDeps -- inherited)
    }

    filteredAllDepsByConfig
  }

  // Move back to coursier.util (in core module) after 1.0?
  private def dependenciesWithConfig(
    res: Map[Configuration, Resolution],
    depsByConfig: Map[Configuration, Seq[Dependency]],
    configs: Map[Configuration, Set[Configuration]]
  ): Set[Dependency] =
    allDependenciesByConfig(res, depsByConfig, configs)
      .flatMap {
        case (config, deps) =>
          deps.map(dep => dep.copy(configuration = config --> dep.configuration))
      }
      .groupBy(_.copy(configuration = Configuration.empty))
      .map {
        case (dep, l) =>
          dep.copy(configuration = Configuration.join(l.map(_.configuration).toSeq: _*))
      }
      .toSet

  def update(
    params: UpdateParams,
    verbosityLevel: Int,
    log: Logger
  ): UpdateReport = Lock.lock.synchronized {

    val configResolutions = params.res.flatMap {
      case (configs, r) =>
        configs.iterator.map((_, r))
    }

    val depsByConfig = grouped(params.dependencies)(
      config =>
        params.shadedConfigOpt match {
          case Some((baseConfig, `config`)) =>
            Configuration(baseConfig)
          case _ =>
            config
        }
    )

    if (verbosityLevel >= 2) {
      val finalDeps = dependenciesWithConfig(
        configResolutions,
        depsByConfig,
        params.configs
      )

      val projCache = params.res.values.foldLeft(Map.empty[ModuleVersion, Project])(_ ++ _.projectCache.mapValues(_._2))
      val repr = Print.dependenciesUnknownConfigs(finalDeps.toVector, projCache)
      log.info(repr.split('\n').map("  " + _).mkString("\n"))
    }

    SbtUpdateReport(
      depsByConfig,
      configResolutions,
      params.configs,
      params.classifiers,
      params.artifactFileOpt,
      log,
      includeSignatures = params.includeSignatures
    )
  }

  private def grouped[K, V](map: Seq[(K, V)])(mapKey: K => K): Map[K, Seq[V]] =
    map
      .groupBy(t => mapKey(t._1))
      .mapValues(_.map(_._2))
      .iterator
      .toMap

}
