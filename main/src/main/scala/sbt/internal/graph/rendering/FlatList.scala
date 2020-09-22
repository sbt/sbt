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

object FlatList {
  def render(display: Module => String)(graph: ModuleGraph): String =
    graph.modules.values.toSeq.distinct
      .filterNot(_.isEvicted)
      .sortBy(m => (m.id.organization, m.id.name))
      .map(display)
      .mkString("\n")
}
