/*
 * Copyright 2011, 2012 Johannes Rudolph
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

object GraphTransformations {
  def reverseGraphStartingAt(graph: ModuleGraph, root: ModuleId): ModuleGraph = {
    val deps = graph.reverseDependencyMap

    def visit(module: ModuleId, visited: Set[ModuleId]): Seq[(ModuleId, ModuleId)] =
      if (visited(module))
        Nil
      else
        deps.get(module) match {
          case Some(deps) ⇒
            deps.flatMap { to ⇒
              (module, to.id) +: visit(to.id, visited + module)
            }
          case None ⇒ Nil
        }

    val edges = visit(root, Set.empty)
    val nodes = edges.foldLeft(Set.empty[ModuleId])((set, edge) ⇒ set + edge._1 + edge._2).map(graph.module)
    ModuleGraph(nodes.toSeq, edges)
  }

  def ignoreScalaLibrary(scalaVersion: String, graph: ModuleGraph): ModuleGraph = {
    def isScalaLibrary(m: Module) = isScalaLibraryId(m.id)
    def isScalaLibraryId(id: ModuleId) = id.organisation == "org.scala-lang" && id.name == "scala-library"

    def dependsOnScalaLibrary(m: Module): Boolean =
      graph.dependencyMap(m.id).exists(isScalaLibrary)

    def addScalaLibraryAnnotation(m: Module): Module = {
      if (dependsOnScalaLibrary(m))
        m.copy(extraInfo = m.extraInfo + " [S]")
      else
        m
    }

    val newNodes = graph.nodes.map(addScalaLibraryAnnotation).filterNot(isScalaLibrary)
    val newEdges = graph.edges.filterNot(e ⇒ isScalaLibraryId(e._2))
    ModuleGraph(newNodes, newEdges)
  }
}
