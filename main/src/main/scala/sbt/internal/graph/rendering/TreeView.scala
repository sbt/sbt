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

import sbt.internal.graph.codec.JsonProtocol.ModuleModelFormat
import sbt.io.IO
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }

import java.io.{ File, FileOutputStream, InputStream, OutputStream }
import java.net.URI
import scala.collection.mutable
import scala.util.Using

object TreeView {
  def createJson(graph: ModuleGraph): String = {
    val moduleModels = graph.roots.map(module => processSubtree(graph, module))
    val js = moduleModels.map(Converter.toJsonUnsafe(_))
    js.map(CompactPrinter).mkString("[", ",", "]")
  }

  def createLink(graphJson: String, targetDirectory: File): URI = {
    val graphHTML = createFile(graphJson, targetDirectory)
    new URI(graphHTML.toURI.toString)
  }

  def createFile(graphJson: String, targetDirectory: File): File = {
    targetDirectory.mkdirs()
    val graphHTML = new File(targetDirectory, "tree.html")
    saveResource("tree.html", graphHTML)
    IO.write(new File(targetDirectory, "tree.json"), graphJson, IO.utf8)
    IO.write(new File(targetDirectory, "tree.data.js"), s"tree_data = $graphJson;", IO.utf8)
    graphHTML
  }

  private[rendering] def processSubtree(
      graph: ModuleGraph,
      module: Module,
      parents: Set[GraphModuleId] = Set()
  ): ModuleModel =
    processSubtreeImpl(graph, module, parents, mutable.Set.empty[GraphModuleId])

  // `visited` is owned by the caller; do not share it across renders.
  private def processSubtreeImpl(
      graph: ModuleGraph,
      module: Module,
      parents: Set[GraphModuleId],
      visited: mutable.Set[GraphModuleId]
  ): ModuleModel = {
    val cycle = parents.contains(module.id)
    val duplicate = !cycle && visited.contains(module.id)
    val dependencies =
      if (cycle || duplicate) List()
      else {
        visited += module.id
        graph.dependencyMap.getOrElse(module.id, List())
      }
    val children =
      dependencies
        .map(dependency => processSubtreeImpl(graph, dependency, parents + module.id, visited))
        .toVector
    ModuleModel(displayText(module, cycle, duplicate), children)
  }

  // Suffix order is pinned by TreeViewTest "concatenate marker suffixes
  // in a stable order"; change here means change the test.
  private def displayText(module: Module, isCycle: Boolean, isDuplicate: Boolean): String = {
    val suffixes = Vector(
      module.evictedByVersion.map(v => s" (evicted by $v)"),
      module.error.map(err => s" (errors: $err)"),
      if (isCycle) Some(" (cycle)") else None,
      if (isDuplicate) Some(" (*)") else None,
    ).flatten
    module.id.idString + suffixes.mkString
  }

  def saveResource(resourcePath: String, to: File): Unit = {
    val is = getClass.getClassLoader.getResourceAsStream(resourcePath)
    require(is ne null, s"Couldn't load '$resourcePath' from classpath.")

    Using.resource(new FileOutputStream(to)) { fos =>
      try copy(is, fos)
      finally {
        is.close()
      }
    }
  }

  def copy(from: InputStream, to: OutputStream): Unit =
    from.transferTo(to)
}
