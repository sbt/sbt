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

package net.virtualvoid.sbt.graph.rendering

import net.virtualvoid.sbt.graph.ModuleGraph

import scala.xml.XML

object GraphML {
  def saveAsGraphML(graph: ModuleGraph, outputFile: String): Unit = {
    val nodesXml =
      for (n ← graph.nodes)
        yield <node id={ n.id.idString }><data key="d0">
                                           <y:ShapeNode>
                                             <y:NodeLabel>{ n.id.idString }</y:NodeLabel>
                                           </y:ShapeNode>
                                         </data></node>

    val edgesXml =
      for (e ← graph.edges)
        yield <edge source={ e._1.idString } target={ e._2.idString }/>

    val xml =
      <graphml xmlns="http://graphml.graphdrawing.org/xmlns" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:y="http://www.yworks.com/xml/graphml" xsi:schemaLocation="http://graphml.graphdrawing.org/xmlns http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd">
        <key for="node" id="d0" yfiles.type="nodegraphics"/>
        <graph id="Graph" edgedefault="undirected">
          { nodesXml }
          { edgesXml }
        </graph>
      </graphml>

    XML.save(outputFile, xml)
  }
}
