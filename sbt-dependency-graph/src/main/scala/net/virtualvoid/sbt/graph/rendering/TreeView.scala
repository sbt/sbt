package net.virtualvoid.sbt.graph.rendering

import java.io.File
import java.net.URI

import net.virtualvoid.sbt.graph.util.IOUtil
import net.virtualvoid.sbt.graph.{ Module, ModuleGraph }

import scala.util.parsing.json.{ JSONArray, JSONObject }

object TreeView {
  def createJson(graph: ModuleGraph): String = {
    val trees = graph.roots
      .map(module ⇒ processSubtree(graph, module))
      .toList
    JSONArray(trees).toString
  }

  def createLink(graphJson: String, targetDirectory: File): URI = {
    targetDirectory.mkdirs()
    val graphHTML = new File(targetDirectory, "tree.html")
    IOUtil.saveResource("tree.html", graphHTML)
    IOUtil.writeToFile(graphJson, new File(targetDirectory, "tree.json"))
    IOUtil.writeToFile(s"tree_data = $graphJson;", new File(targetDirectory, "tree.data.js"))
    new URI(graphHTML.toURI.toString)
  }

  private def processSubtree(graph: ModuleGraph, module: Module): JSONObject = {
    val children = graph.dependencyMap
      .getOrElse(module.id, List())
      .map(module ⇒ processSubtree(graph, module))
      .toList
    moduleAsJson(module, children)
  }

  private def moduleAsJson(module: Module, children: List[JSONObject]): JSONObject = {
    val eviction = module.evictedByVersion.map(version ⇒ s" (evicted by $version)").getOrElse("")
    val error = module.error.map(err ⇒ s" (errors: $err)").getOrElse("")
    val text = module.id.idString + eviction + error
    JSONObject(Map("text" -> text, "children" -> JSONArray(children)))
  }
}
