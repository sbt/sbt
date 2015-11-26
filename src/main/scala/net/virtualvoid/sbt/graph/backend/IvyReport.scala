/*
 * Copyright 2015 Johannes Rudolph
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
package backend

import scala.xml.{ NodeSeq, Document, Node }
import scala.xml.parsing.ConstructingParser

object IvyReport {
  def fromReportFile(ivyReportFile: String): ModuleGraph =
    fromReportXML(loadXML(ivyReportFile))

  def fromReportXML(doc: Document): ModuleGraph = {
    def edgesForModule(id: ModuleId, revision: NodeSeq): Seq[Edge] =
      for {
        caller ← revision \ "caller"
        callerModule = moduleIdFromElement(caller, caller.attribute("callerrev").get.text)
      } yield (moduleIdFromElement(caller, caller.attribute("callerrev").get.text), id)

    val moduleEdges: Seq[(Module, Seq[Edge])] = for {
      mod ← doc \ "dependencies" \ "module"
      revision ← mod \ "revision"
      rev = revision.attribute("name").get.text
      moduleId = moduleIdFromElement(mod, rev)
      module = Module(moduleId,
        (revision \ "license").headOption.flatMap(_.attribute("name")).map(_.text),
        evictedByVersion = (revision \ "evicted-by").headOption.flatMap(_.attribute("rev").map(_.text)),
        error = revision.attribute("error").map(_.text))
    } yield (module, edgesForModule(moduleId, revision))

    val (nodes, edges) = moduleEdges.unzip

    val info = (doc \ "info").head
    def infoAttr(name: String): String =
      info.attribute(name).getOrElse(throw new IllegalArgumentException("Missing attribute " + name)).text
    val rootModule = Module(ModuleId(infoAttr("organisation"), infoAttr("module"), infoAttr("revision")))

    ModuleGraph(rootModule +: nodes, edges.flatten)
  }

  private def moduleIdFromElement(element: Node, version: String): ModuleId =
    ModuleId(element.attribute("organisation").get.text, element.attribute("name").get.text, version)

  private def loadXML(ivyReportFile: String) =
    ConstructingParser.fromSource(io.Source.fromFile(ivyReportFile), preserveWS = false).document()
}
