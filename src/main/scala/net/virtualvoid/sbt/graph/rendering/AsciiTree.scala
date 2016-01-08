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

import util.AsciiTreeLayout
import util.ConsoleUtils._

object AsciiTree {
  def asciiTree(graph: ModuleGraph): String = {
    val deps = graph.dependencyMap

    // there should only be one root node (the project itself)
    val roots = graph.roots
    roots.map { root ⇒
      AsciiTreeLayout.toAscii[Module](root, node ⇒ deps.getOrElse(node.id, Seq.empty[Module]), displayModule)
    }.mkString("\n")
  }

  def displayModule(module: Module): String =
    red(module.id.idString +
      module.extraInfo +
      module.error.map(" (error: " + _ + ")").getOrElse("") +
      module.evictedByVersion.map(_ formatted " (evicted by: %s)").getOrElse(""), module.hadError)
}
