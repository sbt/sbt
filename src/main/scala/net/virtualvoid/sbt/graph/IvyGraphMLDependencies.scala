package net.virtualvoid.sbt.graph

import xml.parsing.ConstructingParser
import java.io.File
import xml.{XML, Node}

object IvyGraphMLDependencies extends App {
  case class Module(organisation: String, name: String) {
    def id: String = organisation+"."+name
  }
  def transform(ivyReportFile: String, outputFile: String) {
    val doc = ConstructingParser.fromSource(io.Source.fromFile(ivyReportFile), false).document

    val edges =
      for (mod <- doc \ "dependencies" \ "module";
           depModule = nodeFromElement(mod);
           caller <- mod \ "revision" \ "caller";
           callerModule = nodeFromElement(caller))
        yield (callerModule, depModule)

    val nodes = edges.flatMap(e => Seq(e._1, e._2)).distinct

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
  def nodeFromElement(element: Node): Module =
    Module(element.attribute("organisation").get.text, element.attribute("name").get.text)

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