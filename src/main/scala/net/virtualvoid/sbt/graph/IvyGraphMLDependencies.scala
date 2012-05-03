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

import xml.parsing.ConstructingParser
import java.io.File
import collection.mutable.HashMap
import collection.mutable.MultiMap
import collection.mutable.{Set => MSet}
import sbt.Graph
import xml.{Document, XML, Node}

object IvyGraphMLDependencies extends App {
  case class Module(organisation: String, name: String, version: String) {
    def id: String = organisation+":"+name+":"+version
  }

  case class ModuleGraph(nodes: Seq[Module], edges: Seq[(Module, Module)])

  def buildGraph(doc: Document): ModuleGraph = {
    val edges = for {
      mod <- doc \ "dependencies" \ "module"
      caller <- mod \ "revision" \ "caller"
      callerModule = nodeFromElement(caller, caller.attribute("callerrev").get.text)
      depModule = nodeFromElement(mod, caller.attribute("rev").get.text)
    } yield (callerModule, depModule)

    val nodes = edges.flatMap(e => Seq(e._1, e._2)).distinct

    ModuleGraph(nodes, edges)
  }


  def ascii(ivyReportFile: String): String = {
    val doc = buildDoc(ivyReportFile)
    val graph = buildGraph(doc)
    import graph._
    val deps = {
      val m = new HashMap[Module, MSet[Module]] with MultiMap[Module, Module]
      edges.foreach { case (from, to) => m.addBinding(from, to) }
      m.toMap.mapValues(_.toSeq.sortBy(_.id))
    }
    // there should only be one root node (the project itself)
    val roots = nodes.filter(n => !edges.exists(_._2 == n)).sortBy(_.id)
    roots.map(root =>
      Graph.toAscii[Module](root, node => deps.getOrElse(node, Seq.empty[Module]), _.id)
    ).mkString("\n")
  }

  def transform(ivyReportFile: String, outputFile: String) {
    val doc = buildDoc(ivyReportFile)
    val graph = buildGraph(doc)
    import graph._

    val nodesXml =
      for (n <- nodes)
        yield
          <node id={n.id}><data key="d0">
            <y:ShapeNode>
              <y:NodeLabel>{n.id}</y:NodeLabel>
            </y:ShapeNode>
          </data></node>

    val edgesXml =
      for (e <- edges)
        yield <edge source={e._1.id} target={e._2.id} />

    val xml =
      <graphml xmlns="http://graphml.graphdrawing.org/xmlns"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xmlns:y="http://www.yworks.com/xml/graphml"
               xsi:schemaLocation="http://graphml.graphdrawing.org/xmlns http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd">
        <key for="node" id="d0" yfiles.type="nodegraphics"/>
        <graph id="Graph" edgedefault="undirected">
          {nodesXml}
          {edgesXml}
        </graph>
      </graphml>

    XML.save(outputFile, xml)
  }
  private def nodeFromElement(element: Node, version: String): Module =
    Module(element.attribute("organisation").get.text, element.attribute("name").get.text, version)

  private def buildDoc(ivyReportFile: String) = ConstructingParser.fromSource(io.Source.fromFile(ivyReportFile), false).document

  def die(msg: String): Nothing = {
    println(msg)
    sys.exit(1)
  }
  def usage: String =
    "Usage: <ivy-report-file> <output-file>"

  val file = args.lift(0).filter(f => new File(f).exists).getOrElse(die(usage))
  val inputFile = args.lift(1).getOrElse(die(usage))
  transform(file, inputFile)
}
