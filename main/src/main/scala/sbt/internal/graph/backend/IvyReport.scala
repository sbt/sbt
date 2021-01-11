/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package graph
package backend

import scala.xml.{ NodeSeq, Document, Node }
import scala.xml.parsing.ConstructingParser

object IvyReport {
  def fromReportFile(ivyReportFile: String): ModuleGraph =
    fromReportXML(loadXML(ivyReportFile))

  def fromReportXML(doc: Document): ModuleGraph = {
    def edgesForModule(id: GraphModuleId, revision: NodeSeq): Seq[Edge] =
      for {
        caller <- revision \ "caller"
        callerModule = moduleIdFromElement(caller, caller.attribute("callerrev").get.text)
      } yield (moduleIdFromElement(caller, caller.attribute("callerrev").get.text), id)

    val moduleEdges: Seq[(Module, Seq[Edge])] = for {
      mod <- doc \ "dependencies" \ "module"
      revision <- mod \ "revision"
      rev = revision.attribute("name").get.text
      moduleId = moduleIdFromElement(mod, rev)
      module = Module(
        moduleId,
        (revision \ "license").headOption.flatMap(_.attribute("name")).map(_.text),
        evictedByVersion =
          (revision \ "evicted-by").headOption.flatMap(_.attribute("rev").map(_.text)),
        error = revision.attribute("error").map(_.text)
      )
    } yield (module, edgesForModule(moduleId, revision))

    val (nodes, edges) = moduleEdges.unzip

    val info = (doc \ "info").head
    def infoAttr(name: String): String =
      info
        .attribute(name)
        .getOrElse(throw new IllegalArgumentException("Missing attribute " + name))
        .text
    val rootModule = Module(
      GraphModuleId(infoAttr("organization"), infoAttr("module"), infoAttr("revision"))
    )

    ModuleGraph(rootModule +: nodes, edges.flatten)
  }

  private def moduleIdFromElement(element: Node, version: String): GraphModuleId =
    GraphModuleId(
      element.attribute("organization").get.text,
      element.attribute("name").get.text,
      version
    )

  private def loadXML(ivyReportFile: String) =
    ConstructingParser
      .fromSource(scala.io.Source.fromFile(ivyReportFile), preserveWS = false)
      .document()
}
