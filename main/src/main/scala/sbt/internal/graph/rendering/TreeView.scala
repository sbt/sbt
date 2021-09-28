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

import sbt.io.IO

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import scala.annotation.nowarn
import scala.annotation.tailrec
import scala.util.parsing.json.JSONArray
import scala.util.parsing.json.JSONObject

@nowarn object TreeView {
  def createJson(graph: ModuleGraph): String = {
    val trees = graph.roots
      .map(module => processSubtree(graph, module))
      .toList
    JSONArray(trees).toString
  }

  def createLink(graphJson: String, targetDirectory: File): URI = {
    targetDirectory.mkdirs()
    val graphHTML = new File(targetDirectory, "tree.html")
    saveResource("tree.html", graphHTML)
    IO.write(new File(targetDirectory, "tree.json"), graphJson, IO.utf8)
    IO.write(new File(targetDirectory, "tree.data.js"), s"tree_data = $graphJson;", IO.utf8)
    new URI(graphHTML.toURI.toString)
  }

  private[rendering] def processSubtree(
      graph: ModuleGraph,
      module: Module,
      parents: Set[GraphModuleId] = Set()
  ): JSONObject = {
    val cycle = parents.contains(module.id)
    val dependencies = if (cycle) List() else graph.dependencyMap.getOrElse(module.id, List())
    val children =
      dependencies.map(dependency => processSubtree(graph, dependency, parents + module.id)).toList
    moduleAsJson(module, cycle, children)
  }

  private def moduleAsJson(
      module: Module,
      isCycle: Boolean,
      children: List[JSONObject]
  ): JSONObject = {
    val eviction = module.evictedByVersion.map(version => s" (evicted by $version)").getOrElse("")
    val cycle = if (isCycle) " (cycle)" else ""
    val error = module.error.map(err => s" (errors: $err)").getOrElse("")
    val text = module.id.idString + eviction + error + cycle
    JSONObject(Map("text" -> text, "children" -> JSONArray(children)))
  }

  def saveResource(resourcePath: String, to: File): Unit = {
    val is = getClass.getClassLoader.getResourceAsStream(resourcePath)
    require(is ne null, s"Couldn't load '$resourcePath' from classpath.")

    val fos = new FileOutputStream(to)
    try copy(is, fos)
    finally {
      is.close()
      fos.close()
    }
  }

  def copy(from: InputStream, to: OutputStream): Unit = {
    val buffer = new Array[Byte](65536)

    @tailrec def rec(): Unit = {
      val read = from.read(buffer)
      if (read > 0) {
        to.write(buffer, 0, read)
        rec()
      } else if (read == 0)
        throw new IllegalStateException("InputStream.read returned 0")
    }
    rec()
  }
}
