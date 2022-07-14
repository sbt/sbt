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

import sbt.internal.graph.codec.JsonProtocol.ModuleModelFormat
import sbt.io.IO
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }

import java.io.{ File, FileOutputStream, InputStream, OutputStream }
import java.net.URI
import scala.annotation.{ nowarn, tailrec }

@nowarn object TreeView {
  def createJson(graph: ModuleGraph): String = {
    val moduleModels = graph.roots
      .map(module => processSubtree(graph, module))
    val js = moduleModels.map(Converter.toJsonUnsafe(_))
    js.map(CompactPrinter).mkString("[", ",", "]")
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
  ): ModuleModel = {
    val cycle = parents.contains(module.id)
    val dependencies = if (cycle) List() else graph.dependencyMap.getOrElse(module.id, List())
    val children =
      dependencies
        .map(dependency => processSubtree(graph, dependency, parents + module.id))
        .toVector
    moduleAsModuleAgain(module, cycle, children)
  }

  private def moduleAsModuleAgain(
      module: Module,
      isCycle: Boolean,
      children: Vector[ModuleModel]
  ): ModuleModel = {
    val eviction = module.evictedByVersion.map(version => s" (evicted by $version)").getOrElse("")
    val cycle = if (isCycle) " (cycle)" else ""
    val error = module.error.map(err => s" (errors: $err)").getOrElse("")
    val text = module.id.idString + eviction + error + cycle
    ModuleModel(text, children)
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
