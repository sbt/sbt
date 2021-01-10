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

import scala.xml.XML

object GraphML {
  def saveAsGraphML(graph: ModuleGraph, outputFile: String): Unit = {
    val nodesXml =
      for (n <- graph.nodes)
        yield <node id={n.id.idString}><data key="d0">
                                           <y:ShapeNode>
                                             <y:NodeLabel>{n.id.idString}</y:NodeLabel>
                                           </y:ShapeNode>
                                         </data></node>

    val edgesXml =
      for (e <- graph.edges)
        yield <edge source={e._1.idString} target={e._2.idString}/>

    val xml =
      <graphml xmlns="http://graphml.graphdrawing.org/xmlns" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:y="http://www.yworks.com/xml/graphml" xsi:schemaLocation="http://graphml.graphdrawing.org/xmlns http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd">
        <key for="node" id="d0" yfiles.type="nodegraphics"/>
        <graph id="Graph" edgedefault="undirected">
          {nodesXml}
          {edgesXml}
        </graph>
      </graphml>

    XML.save(outputFile, xml)
  }
}
