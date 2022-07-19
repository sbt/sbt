/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package graph

object GraphTransformations {
  def reverseGraphStartingAt(graph: ModuleGraph, root: GraphModuleId): ModuleGraph = {
    val deps = graph.reverseDependencyMap

    def visit(
        module: GraphModuleId,
        visited: Set[GraphModuleId]
    ): Seq[(GraphModuleId, GraphModuleId)] =
      if (visited(module))
        Nil
      else
        deps.get(module) match {
          case Some(deps) =>
            deps.flatMap { to =>
              (module, to.id) +: visit(to.id, visited + module)
            }
          case None => Nil
        }

    val edges = visit(root, Set.empty)
    val nodes =
      edges
        .foldLeft(Set.empty[GraphModuleId])((set, edge) => set + edge._1 + edge._2)
        .flatMap(graph.module)
    ModuleGraph(nodes.toSeq, edges)
  }

  def ignoreScalaLibrary(scalaVersion: String, graph: ModuleGraph): ModuleGraph = {
    def isScalaLibrary(m: Module) = isScalaLibraryId(m.id)
    def isScalaLibraryId(id: GraphModuleId) =
      id.organization == "org.scala-lang" && id.name == "scala-library"

    def dependsOnScalaLibrary(m: Module): Boolean =
      graph.dependencyMap(m.id).exists(isScalaLibrary)

    def addScalaLibraryAnnotation(m: Module): Module = {
      if (dependsOnScalaLibrary(m))
        m.copy(extraInfo = m.extraInfo + " [S]")
      else
        m
    }

    val newNodes = graph.nodes.map(addScalaLibraryAnnotation).filterNot(isScalaLibrary)
    val newEdges = graph.edges.filterNot(e => isScalaLibraryId(e._2))
    ModuleGraph(newNodes, newEdges)
  }
}
