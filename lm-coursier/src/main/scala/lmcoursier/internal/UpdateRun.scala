package lmcoursier.internal

import coursier.cache.loggers.RefreshLogger
import coursier.core.Resolution.ModuleVersion
import coursier.core.*
import coursier.util.Print
import sbt.librarymanagement.UpdateReport
import sbt.util.Logger
import scala.annotation.nowarn

// private[coursier]
object UpdateRun {

  // Move back to coursier.util (in core module) after 1.0?
  @nowarn
  private def allDependenciesByConfig(
      res: Map[Configuration, Resolution],
      depsByConfig: Map[Configuration, Seq[Dependency]],
      configs: Map[Configuration, Set[Configuration]]
  ): Map[Configuration, Set[Dependency]] = {

    val allDepsByConfig = depsByConfig.map { (config, deps) =>
      config -> res(config).subset(deps).minDependencies
    }

    val filteredAllDepsByConfig = allDepsByConfig.map { (config, allDeps) =>
      val allExtendedConfigs = configs.getOrElse(config, Set.empty) - config
      val inherited = allExtendedConfigs
        .flatMap(allDepsByConfig.getOrElse(_, Set.empty))

      config -> (allDeps -- inherited)
    }

    filteredAllDepsByConfig
  }

  // Move back to coursier.util (in core module) after 1.0?
  @nowarn
  private def dependenciesWithConfig(
      res: Map[Configuration, Resolution],
      depsByConfig: Map[Configuration, Seq[Dependency]],
      configs: Map[Configuration, Set[Configuration]]
  ): Set[Dependency] =
    allDependenciesByConfig(res, depsByConfig, configs)
      .flatMap { (config, deps) =>
        deps.map(dep => dep.withConfiguration(config --> dep.configuration))
      }
      .groupBy(_.withConfiguration(Configuration.empty))
      .map { (dep, l) =>
        dep.withConfiguration(Configuration.join(l.map(_.configuration).toSeq*))
      }
      .toSet

  @nowarn
  def update(
      params: UpdateParams,
      verbosityLevel: Int,
      log: Logger
  ): UpdateReport = Lock.maybeSynchronized(needsLock = !RefreshLogger.defaultFallbackMode) {
    val depsByConfig = grouped(params.dependencies)

    if (verbosityLevel >= 2) {
      val finalDeps = dependenciesWithConfig(
        params.res,
        depsByConfig,
        params.configs
      )

      val projCache = params.res.values.foldLeft(Map.empty[ModuleVersion, Project])(
        _ ++ _.projectCache.view.mapValues(_._2).toMap
      )
      val repr = Print.dependenciesUnknownConfigs(finalDeps.toVector, projCache)
      log.info(repr.split('\n').map("  " + _).mkString("\n"))
    }

    SbtUpdateReport(
      params.thisModule,
      depsByConfig,
      params.res.toVector.sortBy(_._1.value), // FIXME Order by config topologically?
      params.interProjectDependencies.toVector,
      params.classifiers,
      params.artifactFileOpt,
      params.fullArtifacts,
      log,
      includeSignatures = params.includeSignatures,
      classpathOrder = params.classpathOrder,
      missingOk = params.missingOk,
      params.forceVersions,
      params.classLoaders,
    )
  }

  private def grouped[K, V](map: Seq[(K, V)]): Map[K, Seq[V]] =
    map.groupMap(_._1)((_, values) => values)

}
