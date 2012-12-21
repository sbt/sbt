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
import xml.{NodeSeq, Document, XML, Node}
import com.github.mdr.ascii.layout
import layout._
import sbinary.{Format, DefaultProtocol}

object IvyGraphMLDependencies extends App {
  case class ModuleId(organisation: String,
                      name: String,
                      version: String) {
    def idString: String = organisation+":"+name+":"+version
  }
  case class Module(id: ModuleId,
                    license: Option[String] = None,
                    extraInfo: String = "",
                    evictedByVersion: Option[String] = None,
                    error: Option[String] = None) {
    def hadError: Boolean = error.isDefined
    def isUsed: Boolean = !evictedByVersion.isDefined
  }

  type Edge = (ModuleId, ModuleId)

  case class ModuleGraph(nodes: Seq[Module], edges: Seq[Edge]) {
    lazy val modules: Map[ModuleId, Module] =
      nodes.map(n => (n.id, n)).toMap

    def module(id: ModuleId): Module = modules(id)

    lazy val dependencyMap: Map[ModuleId, Seq[Module]] =
      createMap(identity)

    lazy val reverseDependencyMap: Map[ModuleId, Seq[Module]] =
      createMap { case (a, b) => (b, a) }

    def createMap(bindingFor: ((ModuleId, ModuleId)) => (ModuleId, ModuleId)): Map[ModuleId, Seq[Module]] = {
      val m = new HashMap[ModuleId, MSet[Module]] with MultiMap[ModuleId, Module]
      edges.foreach { entry =>
        val (f, t) = bindingFor(entry)
        m.addBinding(f, module(t))
      }
      m.toMap.mapValues(_.toSeq.sortBy(_.id.idString)).withDefaultValue(Nil)
    }
  }

  def graph(ivyReportFile: String): ModuleGraph =
    buildGraph(buildDoc(ivyReportFile))

  def buildGraph(doc: Document): ModuleGraph = {
    def edgesForModule(id: ModuleId, revision: NodeSeq, rev: String): Seq[Edge] =
      for {
        caller      <- revision \ "caller" if caller.attribute("rev").get.text == rev
        callerModule = moduleIdFromElement(caller, caller.attribute("callerrev").get.text)
      } yield (moduleIdFromElement(caller, caller.attribute("callerrev").get.text), id)

    val moduleEdges: Seq[(Module, Seq[Edge])] = for {
      mod      <- doc \ "dependencies" \ "module"
      revision <- mod \ "revision"
      rev       = revision.attribute("name").get.text
      moduleId  = moduleIdFromElement(mod, rev)
      module    = Module(moduleId,
                         (revision \ "license").headOption.flatMap(_.attribute("name")).map(_.text),
                         evictedByVersion = (revision \ "evicted-by").headOption.flatMap(_.attribute("rev").map(_.text)),
                         error = revision.attribute("error").map(_.text))
    } yield (module, edgesForModule(moduleId, revision, rev))

    val (nodes, edges) = moduleEdges.unzip

    val info = (doc \ "info").head
    def infoAttr(name: String): String =
      info.attribute(name).getOrElse(throw new IllegalArgumentException("Missing attribute "+name)).text
    val rootModule = Module(ModuleId(infoAttr("organisation"), infoAttr("module"), infoAttr("revision")))

    ModuleGraph(rootModule +: nodes, edges.flatten)
  }

  def reverseGraphStartingAt(graph: ModuleGraph, root: ModuleId): ModuleGraph = {
    val deps = graph.reverseDependencyMap

    def visit(module: ModuleId, visited: Set[ModuleId]): Seq[(ModuleId, ModuleId)] =
      if (visited(module))
        Nil
      else
        deps.get(module) match {
          case Some(deps) =>
            deps.flatMap { to =>
              (module, to.id) +: visit(to.id, visited + module)
            }
          case None => Nil
        }

    val edges = visit(root, Set.empty)
    val nodes = edges.foldLeft(Set.empty[ModuleId])((set, edge) => set + edge._1 + edge._2).map(graph.module)
    ModuleGraph(nodes.toSeq, edges)
  }

  def ignoreScalaLibrary(scalaVersion: String, graph: ModuleGraph): ModuleGraph = {
    val scalaLibraryId = ModuleId("org.scala-lang", "scala-library", scalaVersion)

    def dependsOnScalaLibrary(m: Module): Boolean =
      graph.dependencyMap(m.id).map(_.id).contains(scalaLibraryId)

    def addScalaLibraryAnnotation(m: Module): Module = {
      if (dependsOnScalaLibrary(m))
        m.copy(extraInfo = m.extraInfo + " [S]")
      else
        m
    }

    val newNodes = graph.nodes.map(addScalaLibraryAnnotation).filterNot(_.id == scalaLibraryId)
    val newEdges = graph.edges.filterNot(_._2 == scalaLibraryId)
    ModuleGraph(newNodes, newEdges)
  }

  def asciiGraph(graph: ModuleGraph): String =
    Layouter.renderGraph(buildAsciiGraph(graph))

  def asciiTree(graph: ModuleGraph): String = {
    val deps = graph.dependencyMap

    // there should only be one root node (the project itself)
    val roots = graph.nodes.filter(n => !graph.edges.exists(_._2 == n.id)).sortBy(_.id.idString)
    roots.map { root =>
      Graph.toAscii[Module](root, node => deps.getOrElse(node.id, Seq.empty[Module]), displayModule)
    }.mkString("\n")
  }

  def displayModule(module: Module): String =
    red(module.id.idString +
        module.extraInfo +
        module.error.map(" (error: "+_+")").getOrElse("") +
        module.evictedByVersion.map(_ formatted " (evicted by: %s)").getOrElse(""), module.hadError)

  private def buildAsciiGraph(moduleGraph: ModuleGraph): layout.Graph[String] = {
    def renderVertex(module: Module): String =
      module.id.name + module.extraInfo + "\n" +
      module.id.organisation + "\n" +
      module.id.version +
      module.error.map("\nerror: "+_).getOrElse("") +
      module.evictedByVersion.map(_ formatted "\nevicted by: %s").getOrElse("")

    val vertices = moduleGraph.nodes.map(renderVertex).toList
    val edges = moduleGraph.edges.toList.map { case (from, to) ⇒ (renderVertex(moduleGraph.module(from)), renderVertex(moduleGraph.module(to))) }
    layout.Graph(vertices, edges)
  }

  def saveAsGraphML(graph: ModuleGraph, outputFile: String) {
    val nodesXml =
      for (n <- graph.nodes)
        yield
          <node id={n.id.idString}><data key="d0">
            <y:ShapeNode>
              <y:NodeLabel>{n.id.idString}</y:NodeLabel>
            </y:ShapeNode>
          </data></node>

    val edgesXml =
      for (e <- graph.edges)
        yield <edge source={e._1.idString} target={e._2.idString} />

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
  def saveAsDot(graph : ModuleGraph,
                dotHead : String,
                nodeFormation : Function3[String,String,String,String],
                outputFile: File
  ) : File = {
    val nodes = {
      for (n <- graph.nodes)
        yield
          """    "%s"[label=%s]""".format(n.id.idString,
            nodeFormation(n.id.organisation, n.id.name, n.id.version))
    }.mkString("\n")

    val edges = {
      for ( e <- graph.edges)
        yield
         """    "%s" -> "%s"""".format(e._1.idString, e._2.idString)
    }.mkString("\n")

    val dot = "%s\n%s\n%s\n}".format(dotHead, nodes, edges)

    sbt.IO.write(outputFile, dot)
    outputFile
  }

  def moduleIdFromElement(element: Node, version: String): ModuleId =
    ModuleId(element.attribute("organisation").get.text, element.attribute("name").get.text, version)

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

  val reportFile = args.lift(0).filter(f => new File(f).exists).getOrElse(die(usage))
  val outputFile = args.lift(1).getOrElse(die(usage))
  saveAsGraphML(graph(reportFile), outputFile)
}

object ModuleGraphProtocol extends DefaultProtocol {
  import IvyGraphMLDependencies._

  implicit def seqFormat[T: Format]: Format[Seq[T]] = wrap[Seq[T], List[T]](_.toList, _.toSeq)
  implicit val ModuleIdFormat: Format[ModuleId] = asProduct3(ModuleId)(ModuleId.unapply(_).get)
  implicit val ModuleFormat: Format[Module] = asProduct5(Module)(Module.unapply(_).get)
  implicit val ModuleGraphFormat: Format[ModuleGraph] = asProduct2(ModuleGraph)(ModuleGraph.unapply(_).get)
}
