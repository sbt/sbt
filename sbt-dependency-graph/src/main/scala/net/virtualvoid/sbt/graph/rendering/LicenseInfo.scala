package net.virtualvoid.sbt.graph.rendering

import net.virtualvoid.sbt.graph.ModuleGraph

object LicenseInfo {
  def render(graph: ModuleGraph): String =
    graph.nodes.filter(_.isUsed).groupBy(_.license).toSeq.sortBy(_._1).map {
      case (license, modules) ⇒
        license.getOrElse("No license specified") + "\n" +
          modules.map(_.id.idString formatted "\t %s").mkString("\n")
    }.mkString("\n\n")
}
