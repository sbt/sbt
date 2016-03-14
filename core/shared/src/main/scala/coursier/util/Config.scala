package coursier.util

import coursier.core.{ Dependency, Resolution }

object Config {

  // loose attempt at minimizing a set of dependencies from various configs
  // `configs` is assumed to be fully unfold
  def allDependenciesByConfig(
    res: Resolution,
    depsByConfig: Map[String, Set[Dependency]],
    configs: Map[String, Set[String]]
  ): Map[String, Set[Dependency]] = {

    val allDepsByConfig = depsByConfig.map {
      case (config, deps) =>
        config -> res.subset(deps).minDependencies
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

  def dependenciesWithConfig(
    res: Resolution,
    depsByConfig: Map[String, Set[Dependency]],
    configs: Map[String, Set[String]]
  ): Set[Dependency] =
    allDependenciesByConfig(res, depsByConfig, configs)
      .flatMap {
        case (config, deps) =>
          deps.map(dep => dep.copy(configuration = s"$config->${dep.configuration}"))
      }
      .groupBy(_.copy(configuration = ""))
      .map {
        case (dep, l) =>
          dep.copy(configuration = l.map(_.configuration).mkString(";"))
      }
      .toSet

}
