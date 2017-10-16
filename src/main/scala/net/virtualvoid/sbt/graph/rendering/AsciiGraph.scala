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

import com.github.mdr.ascii.graph.Graph
import com.github.mdr.ascii.layout.GraphLayout

object AsciiGraph {
  def asciiGraph(graph: ModuleGraph): String =
    GraphLayout.renderGraph(buildAsciiGraph(graph))

  private def buildAsciiGraph(moduleGraph: ModuleGraph): Graph[String] = {
    def renderVertex(module: Module): String =
      module.id.name + module.extraInfo + "\n" +
        module.id.organisation + "\n" +
        module.id.version +
        module.error.map("\nerror: " + _).getOrElse("") +
        module.evictedByVersion.map(_ formatted "\nevicted by: %s").getOrElse("")

    val vertices = moduleGraph.nodes.map(renderVertex).toSet
    val edges = moduleGraph.edges.toList.map { case (from, to) ⇒ (renderVertex(moduleGraph.module(from)), renderVertex(moduleGraph.module(to))) }
    Graph(vertices, edges)
  }
}
