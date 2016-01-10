package coursier.util

import coursier.core.{ Orders, Dependency }

object Print {

  def dependency(dep: Dependency): String =
    s"${dep.module}:${dep.version}:${dep.configuration}"

  def dependenciesUnknownConfigs(deps: Seq[Dependency]): String = {

    val minDeps = Orders.minDependencies(
      deps.toSet,
      _ => Map.empty
    )

    val deps0 = minDeps
      .groupBy(_.copy(configuration = ""))
      .toVector
      .map { case (k, l) =>
        k.copy(configuration = l.toVector.map(_.configuration).sorted.mkString(","))
      }
      .sortBy { dep =>
        (dep.module.organization, dep.module.name, dep.module.toString, dep.version)
      }

    deps0.map(dependency).mkString("\n")
  }

}
