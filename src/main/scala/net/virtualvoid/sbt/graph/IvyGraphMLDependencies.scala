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
import sbt.{ConsoleLogger, Graph}
import xml.{Document, XML, Node}
import com.github.mdr.ascii.layout
import layout._
import sbinary.{Format, DefaultProtocol}

object IvyGraphMLDependencies extends App {
  case class Module(organisation: String, name: String, version: String, error: Option[String] = None) {
    def id: String = organisation+":"+name+":"+version

    def hadError: Boolean = error.isDefined

    override def hashCode(): Int = id.hashCode
    override def equals(p1: Any): Boolean = p1 match {
      case m: Module => id == m.id
      case _ => false
    }
  }

  case class ModuleGraph(nodes: Seq[Module], edges: Seq[(Module, Module)]) {
    lazy val dependencyMap: Map[Module, Seq[Module]] = {
      val m = new HashMap[Module, MSet[Module]] with MultiMap[Module, Module]
      edges.foreach { case (from, to) => m.addBinding(from, to) }
      m.toMap.mapValues(_.toSeq.sortBy(_.id))
    }
    lazy val reverseDependencyMap: Map[Module, Seq[Module]] = {
      val m = new HashMap[Module, MSet[Module]] with MultiMap[Module, Module]
      edges.foreach { case (from, to) => m.addBinding(to, from) }
      m.toMap.mapValues(_.toSeq.sortBy(_.id))
    }
  }

  def graph(ivyReportFile: String): ModuleGraph =
    buildGraph(buildDoc(ivyReportFile))

  def buildGraph(doc: Document): ModuleGraph = {
    val edges = for {
      mod <- doc \ "dependencies" \ "module"
      revision <- mod \ "revision"
      caller <-  revision \ "caller"
      callerModule = nodeFromElement(caller, caller.attribute("callerrev").get.text)
      depModule = nodeFromElement(mod, revision.attribute("name").get.text, revision.attribute("error").map(_.text))
    } yield (callerModule, depModule)

    val nodes = edges.flatMap(e => Seq(e._1, e._2)).distinct

    ModuleGraph(nodes, edges)
  }

  def reverseGraphStartingAt(graph: ModuleGraph, root: Module): ModuleGraph = {
    val deps = graph.reverseDependencyMap

    def visit(module: Module, visited: Set[Module]): Seq[(Module, Module)] =
      if (visited(module))
        Nil
      else
        deps.get(module) match {
          case Some(deps) =>
            deps.flatMap { to =>
              (module, to) +: visit(to, visited + module)
            }
          case None => Nil
        }

    val edges = visit(root, Set.empty)
    val nodes = edges.foldLeft(Set.empty[Module])((set, edge) => set + edge._1 + edge._2)
    ModuleGraph(nodes.toSeq, edges)
  }

  def asciiGraph(graph: ModuleGraph): String =
    Layouter.renderGraph(buildAsciiGraph(graph))

  def asciiTree(graph: ModuleGraph): String = {
    val deps = graph.dependencyMap

    // there should only be one root node (the project itself)
    val roots = graph.nodes.filter(n => !graph.edges.exists(_._2 == n)).sortBy(_.id)
    roots.map { root =>
      Graph.toAscii[Module](root, node => deps.getOrElse(node, Seq.empty[Module]), displayModule)
    }.mkString("\n")
  }

  def displayModule(module: Module): String =
    red(module.id + module.error.map(" (error: "+_+")").getOrElse(""), module.hadError)

  private def buildAsciiGraph(moduleGraph: ModuleGraph): layout.Graph[String] = {
    def renderVertex(module: Module): String =
      module.name + "\n" + module.organisation + "\n" + module.version + module.error.map("\nerror: "+_).getOrElse("")

    val vertices = moduleGraph.nodes.map(renderVertex).toList
    val edges = moduleGraph.edges.toList.map { case (from, to) ⇒ (renderVertex(from), renderVertex(to)) }
    layout.Graph(vertices, edges)
  }

  def saveAsGraphML(graph: ModuleGraph, outputFile: String) {
    val nodesXml =
      for (n <- graph.nodes)
        yield
          <node id={n.id}><data key="d0">
            <y:ShapeNode>
              <y:NodeLabel>{n.id}</y:NodeLabel>
            </y:ShapeNode>
          </data></node>

    val edgesXml =
      for (e <- graph.edges)
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
  private def nodeFromElement(element: Node, version: String, error: Option[String] = None): Module =
    Module(element.attribute("organisation").get.text, element.attribute("name").get.text, version, error)

  private def buildDoc(ivyReportFile: String) = ConstructingParser.fromSource(io.Source.fromFile(ivyReportFile), false).document

  def red(str: String, doRed: Boolean): String =
    if (ConsoleLogger.formatEnabled && doRed)
      Console.RED + str + Console.RESET
    else
      str

  def die(msg: String): Nothing = {
    println(msg)
    sys.exit(1)
  }
  def usage: String =
    "Usage: <ivy-report-file> <output-file>"

  val file = args.lift(0).filter(f => new File(f).exists).getOrElse(die(usage))
  val inputFile = args.lift(1).getOrElse(die(usage))
  saveAsGraphML(graph(file), inputFile)
}

object ModuleGraphProtocol extends DefaultProtocol {
  import IvyGraphMLDependencies._

  implicit def seqFormat[T: Format]: Format[Seq[T]] = wrap[Seq[T], List[T]](_.toList, _.toSeq)
  implicit val ModuleFormat: Format[Module] = asProduct4(Module)(Module.unapply(_).get)
  implicit val ModuleGraphFormat: Format[ModuleGraph] = asProduct2(ModuleGraph)(ModuleGraph.unapply(_).get)
}
