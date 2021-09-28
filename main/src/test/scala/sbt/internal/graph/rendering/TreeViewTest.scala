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

import org.scalatest.DiagrammedAssertions
import org.scalatest.FunSuite

import scala.annotation.nowarn
import scala.util.parsing.json.JSONArray
import scala.util.parsing.json.JSONObject

@nowarn("msg=class JSONObject in package json is deprecated")
class TreeViewTest extends FunSuite with DiagrammedAssertions {

  val modA = GraphModuleId("orgA", "nameA", "1.0")
  val modB = GraphModuleId("orgB", "nameB", "2.0")

  val graph = ModuleGraph(
    nodes = Seq(Module(modA), Module(modB)),
    edges = Seq(
      modA -> modA,
      modA -> modB,
    )
  )

  test("TreeView should detect cycles and truncate") {
    val json = TreeView.processSubtree(graph, Module(modA))
    val (rootText, children) = parseTree(json)
    assert(rootText == modA.idString)

    val childrenText = children.map(parseTree).map(_._1)
    val expected = List(s"${modA.idString} (cycle)", modB.idString)
    assert(childrenText == expected)
  }

  @nowarn("cat=unchecked")
  def parseTree(json: JSONObject): (String, List[JSONObject]) = {
    (json.obj.get("text"), json.obj.get("children")) match {
      case (Some(text: String), Some(JSONArray(children: List[JSONObject])))
          if children.forall(_.isInstanceOf[JSONObject]) =>
        text -> children
      case _ =>
        fail("a string field 'text' and an array of objects in 'children' field were expected!")
    }
  }

}
