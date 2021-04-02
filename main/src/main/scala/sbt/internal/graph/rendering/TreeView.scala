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

import java.io.{ OutputStream, InputStream, FileOutputStream, File }
import java.net.URI

import graph.{ Module, ModuleGraph }
import sbt.io.IO

import scala.annotation.{ nowarn, tailrec }
import scala.util.parsing.json.{ JSONArray, JSONObject }

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

  private def processSubtree(graph: ModuleGraph, module: Module): JSONObject = {
    val children = graph.dependencyMap
      .getOrElse(module.id, List())
      .map(module => processSubtree(graph, module))
      .toList
    moduleAsJson(module, children)
  }

  private def moduleAsJson(module: Module, children: List[JSONObject]): JSONObject = {
    val eviction = module.evictedByVersion.map(version => s" (evicted by $version)").getOrElse("")
    val error = module.error.map(err => s" (errors: $err)").getOrElse("")
    val text = module.id.idString + eviction + error
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
