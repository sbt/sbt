/*
 * Copyright 2015 Johannes Rudolph
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

import com.github.mdr.ascii.layout._
import net.virtualvoid.sbt.graph.DependencyGraphKeys._
import sbt.Keys._

object AsciiGraph {
  def asciiGraph(graph: ModuleGraph): String =
    Layouter.renderGraph(buildAsciiGraph(graph))

  private def buildAsciiGraph(moduleGraph: ModuleGraph): Graph[String] = {
    def renderVertex(module: Module): String =
      module.id.name + module.extraInfo + "\n" +
        module.id.organisation + "\n" +
        module.id.version +
        module.error.map("\nerror: " + _).getOrElse("") +
        module.evictedByVersion.map(_ formatted "\nevicted by: %s").getOrElse("")

    val vertices = moduleGraph.nodes.map(renderVertex).toList
    val edges = moduleGraph.edges.toList.map { case (from, to) ⇒ (renderVertex(moduleGraph.module(from)), renderVertex(moduleGraph.module(to))) }
    Graph(vertices, edges)
  }

  def asciiGraphSetttings = Seq[sbt.Def.Setting[_]](
    DependencyGraphKeys.asciiGraph := asciiGraph(moduleGraph.value),
    dependencyGraph := {
      val force = DependencyGraphSettings.shouldForceParser.parsed
      val log = streams.value.log
      if (force || moduleGraph.value.nodes.size < 15) {
        log.info(rendering.AsciiGraph.asciiGraph(moduleGraph.value))
        log.info("\n\n")
        log.info("Note: The old tree layout is still available by using `dependency-tree`")
      }

      log.info(rendering.AsciiTree.asciiTree(moduleGraph.value))

      if (!force) {
        log.info("\n")
        log.info("Note: The graph was estimated to be too big to display (> 15 nodes). Use `sbt 'dependency-graph --force'` (with the single quotes) to force graph display.")
      }
    }
  )
}
