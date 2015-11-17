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

package net.virtualvoid.sbt.graph.rendering

import java.io.File

import net.virtualvoid.sbt.graph.ModuleGraph

object DOT {
  def saveAsDot(graph: ModuleGraph,
                dotHead: String,
                nodeFormation: (String, String, String) ⇒ String,
                outputFile: File): File = {
    val nodes = {
      for (n ← graph.nodes)
        yield """    "%s"[label=%s]""".format(n.id.idString,
        nodeFormation(n.id.organisation, n.id.name, n.id.version))
    }.mkString("\n")

    val edges = {
      for (e ← graph.edges)
        yield """    "%s" -> "%s"""".format(e._1.idString, e._2.idString)
    }.mkString("\n")

    val dot = "%s\n%s\n%s\n}".format(dotHead, nodes, edges)

    sbt.IO.write(outputFile, dot)
    outputFile
  }
}
