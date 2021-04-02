/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package graph
package rendering

import sbt.internal.util.Terminal.red

object AsciiTree {
  def asciiTree(graph: ModuleGraph): String = {
    val deps = graph.dependencyMap

    // there should only be one root node (the project itself)
    val roots = graph.roots
    roots
      .map { root =>
        Graph
          .toAscii[Module](
            root,
            node => deps.getOrElse(node.id, Seq.empty[Module]),
            displayModule,
            Graph.defaultColumnSize
          )
      }
      .mkString("\n")
  }

  def displayModule(module: Module): String =
    red(
      module.id.idString +
        module.extraInfo +
        module.error.map(" (error: " + _ + ")").getOrElse("") +
        module.evictedByVersion.map(_ formatted " (evicted by: %s)").getOrElse(""),
      module.hadError
    )
}
