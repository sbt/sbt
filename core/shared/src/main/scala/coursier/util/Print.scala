package coursier.util

import coursier.core.{Module, Project, Orders, Dependency}

object Print {

  def dependency(dep: Dependency): String = {
    val exclusionsStr = dep.exclusions.toVector.sorted.map {
      case (org, name) =>
        s"\n  exclude($org, $name)"
    }.mkString

    s"${dep.module}:${dep.version}:${dep.configuration}$exclusionsStr"
  }

  def dependenciesUnknownConfigs(deps: Seq[Dependency], projects: Map[(Module, String), Project]): String = {

    val deps0 = deps.map { dep =>
      dep.copy(
        version = projects
          .get(dep.moduleVersion)
          .fold(dep.version)(_.version)
      )
    }

    val minDeps = Orders.minDependencies(
      deps0.toSet,
      _ => Map.empty
    )

    val deps1 = minDeps
      .groupBy(_.copy(configuration = ""))
      .toVector
      .map { case (k, l) =>
        k.copy(configuration = l.toVector.map(_.configuration).sorted.mkString(";"))
      }
      .sortBy { dep =>
        (dep.module.organization, dep.module.name, dep.module.toString, dep.version)
      }

    deps1.map(dependency).mkString("\n")
  }

}
