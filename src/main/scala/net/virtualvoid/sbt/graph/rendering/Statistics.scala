/*
 * Copyright 2016 Johannes Rudolph
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.virtualvoid.sbt.graph
package rendering

object Statistics {
  def renderModuleStatsList(graph: ModuleGraph): String = {
    case class ModuleStats(
        id: ModuleId,
        numDirectDependencies: Int,
        numTransitiveDependencies: Int,
        selfSize: Option[Long],
        transitiveSize: Long,
        transitiveDependencyStats: Map[ModuleId, ModuleStats]) {
      def transitiveStatsWithSelf: Map[ModuleId, ModuleStats] = transitiveDependencyStats + (id -> this)
    }

    def statsFor(moduleId: ModuleId): ModuleStats = {
      val directDependencies = graph.dependencyMap(moduleId).filterNot(_.isEvicted).map(_.id)
      val dependencyStats = directDependencies.map(statsFor).flatMap(_.transitiveStatsWithSelf).toMap
      val selfSize = graph.module(moduleId).jarFile.filter(_.exists).map(_.length)
      val numDirectDependencies = directDependencies.size
      val numTransitiveDependencies = dependencyStats.size
      val transitiveSize = selfSize.getOrElse(0L) + dependencyStats.map(_._2.selfSize.getOrElse(0L)).sum

      ModuleStats(moduleId, numDirectDependencies, numTransitiveDependencies, selfSize, transitiveSize, dependencyStats)
    }

    def format(stats: ModuleStats): String = {
      import stats._
      def mb(bytes: Long): Double = bytes.toDouble / 1000000
      val selfSize =
        stats.selfSize match {
          case Some(size) ⇒ f"${mb(size)}%7.3f"
          case None       ⇒ "-------"
        }
      f"${mb(transitiveSize)}%7.3f MB $selfSize MB $numTransitiveDependencies%4d $numDirectDependencies%4d ${id.idString}%s"
    }

    val allStats =
      graph.roots.flatMap(r ⇒ statsFor(r.id).transitiveStatsWithSelf).toMap.values.toSeq
        .sortBy(s ⇒ (-s.transitiveSize, -s.numTransitiveDependencies))

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
