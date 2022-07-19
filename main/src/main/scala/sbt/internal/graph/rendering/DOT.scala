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

object DOT {
  val EvictedStyle = "stroke-dasharray: 5,5"

  def dotGraph(
      graph: ModuleGraph,
      dotHead: String,
      nodeFormation: (String, String, String) => String,
      labelRendering: HTMLLabelRendering
  ): String = {
    val nodes = {
      for (n <- graph.nodes) yield {
        val style = if (n.isEvicted) EvictedStyle else ""
        val label = nodeFormation(n.id.organization, n.id.name, n.id.version)
        """    "%s"[%s style="%s"]""".format(
          n.id.idString,
          labelRendering.renderLabel(label),
          style
        )
      }
    }.sorted.mkString("\n")

    def originWasEvicted(edge: Edge): Boolean = graph.module(edge._1).exists(_.isEvicted)
    def targetWasEvicted(edge: Edge): Boolean = graph.module(edge._2).exists(_.isEvicted)

    // add extra edges from evicted to evicted-by module
    val evictedByEdges: Seq[Edge] =
      graph.nodes
        .filter(_.isEvicted)
        .map(m => Edge(m.id, m.id.copy(version = m.evictedByVersion.get)))

    // remove edges to new evicted-by module which is now replaced by a chain
    // dependend -> [evicted] -> dependee
    val evictionTargetEdges =
      graph.edges.collect {
        case edge @ (from, evicted) if targetWasEvicted(edge) =>
          // Can safely call `get` as `targetWasEvicted` already proves evicted exists in the graph
          (from, evicted.copy(version = graph.module(evicted).flatMap(_.evictedByVersion).get))
      }.toSet

    val filteredEdges =
      graph.edges
        .filterNot(e => originWasEvicted(e) || evictionTargetEdges(e)) ++ evictedByEdges

    val edges = {
      for (e <- filteredEdges) yield {
        val extra =
          if (graph.module(e._1).exists(_.isEvicted))
            s""" [label="Evicted By" style="$EvictedStyle"]"""
          else ""
        """    "%s" -> "%s"%s""".format(e._1.idString, e._2.idString, extra)
      }
    }.sorted.mkString("\n")

    s"$dotHead\n$nodes\n$edges\n}"
  }

  sealed trait HTMLLabelRendering {
    def renderLabel(labelText: String): String
  }

  /**
   *  Render HTML labels in Angle brackets as defined at http://graphviz.org/content/node-shapes#html
   */
  case object AngleBrackets extends HTMLLabelRendering {
    def renderLabel(labelText: String): String = s"label=<$labelText>"
  }

  /**
   * Render HTML labels with `labelType="html"` and label content in double quotes as supported by
   * dagre-d3
   */
  case object LabelTypeHtml extends HTMLLabelRendering {
    def renderLabel(labelText: String): String = s"""labelType="html" label="$labelText""""
  }
}
