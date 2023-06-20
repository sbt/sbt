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

object LicenseInfo {
  def render(graph: ModuleGraph): String =
    graph.nodes
      .filter(_.isUsed)
      .groupBy(_.license)
      .toSeq
      .sortBy(_._1)
      .map {
        case (license, modules) =>
          license.getOrElse("No license specified") + "\n" +
            modules.map(m => s"\t ${m.id.idString}").mkString("\n")
      }
      .mkString("\n\n")
}
