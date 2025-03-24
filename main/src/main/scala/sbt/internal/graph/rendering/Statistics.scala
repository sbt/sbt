/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package graph
package rendering

object Statistics {
  def renderModuleStatsList(graph: ModuleGraph): String = {
    case class ModuleStats(
        id: GraphModuleId,
        numDirectDependencies: Int,
        numTransitiveDependencies: Int,
        selfSize: Option[Long],
        transitiveSize: Long,
        transitiveDependencyStats: Map[GraphModuleId, ModuleStats]
    ) {
      def transitiveStatsWithSelf: Map[GraphModuleId, ModuleStats] =
        transitiveDependencyStats + (id -> this)
    }

    def statsFor(moduleId: GraphModuleId): ModuleStats = {
      val directDependencies = graph.dependencyMap(moduleId).filterNot(_.isEvicted).map(_.id)
      val dependencyStats =
        directDependencies.map(statsFor).flatMap(_.transitiveStatsWithSelf).toMap
      val selfSize = graph.module(moduleId).flatMap(_.jarFile).withFilter(_.exists).map(_.length)
      val numDirectDependencies = directDependencies.size
      val numTransitiveDependencies = dependencyStats.size
      val transitiveSize = selfSize.getOrElse(0L) + dependencyStats
        .map(_._2.selfSize.getOrElse(0L))
        .sum

      ModuleStats(
        moduleId,
        numDirectDependencies,
        numTransitiveDependencies,
        selfSize,
        transitiveSize,
        dependencyStats
      )
    }

    def format(stats: ModuleStats): String = {
      import java.util.Locale
      val dl = Locale.getDefault
      Locale.setDefault(Locale.US)
      import stats.*
      def mb(bytes: Long): Double = bytes.toDouble / 1000000
      val selfSize =
        stats.selfSize match {
          case Some(size) => f"${mb(size)}%7.3f"
          case None       => "-------"
        }
      val r =
        f"${mb(transitiveSize)}%7.3f MB $selfSize MB $numTransitiveDependencies%4d $numDirectDependencies%4d ${id.idString}%s"
      Locale.setDefault(dl)
      r
    }

    val allStats =
      graph.roots
        .flatMap(r => statsFor(r.id).transitiveStatsWithSelf)
        .toMap
        .values
        .toSeq
        .sortBy(s => (-s.transitiveSize, -s.numTransitiveDependencies))

    val header = "   TotSize    JarSize #TDe #Dep Module\n"

    header +
      allStats.map(format).mkString("\n") +
      """
        |
        |Columns are
        | - Jar-Size including dependencies
        | - Jar-Size
        | - Number of transitive dependencies
        | - Number of direct dependencies
        | - ModuleID""".stripMargin
  }
}
