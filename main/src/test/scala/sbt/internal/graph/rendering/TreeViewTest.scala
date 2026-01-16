/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.graph.rendering

import verify.BasicTestSuite
import sbt.internal.graph.rendering.TreeView.createJson
import sbt.internal.graph.{ GraphModuleId, Module, ModuleGraph, ModuleModel }

object TreeViewTest extends BasicTestSuite:
  val modA: GraphModuleId = GraphModuleId("orgA", "nameA", "1.0")
  val modB: GraphModuleId = GraphModuleId("orgB", "nameB", "2.0")
  val modC: GraphModuleId = GraphModuleId("orgC", "nameC", "3.0")

  val graph: ModuleGraph = ModuleGraph(
    nodes = Seq(Module(modA), Module(modB), Module(modC)),
    edges = Seq(
      modA -> modA,
      modA -> modB,
      modC -> modA,
    )
  )

  test("createJson should convert ModuleGraph into JSON correctly"):
    val expected =
      "[{\"text\":\"orgC:nameC:3.0\",\"children\":[{\"text\":\"orgA:nameA:1.0\",\"children\":[{\"text\":\"orgA:nameA:1.0 (cycle)\",\"children\":[]},{\"text\":\"orgB:nameB:2.0\",\"children\":[]}]}]}]"
    Predef.assert(
      createJson(graph) == expected,
      s"Expected $expected, but got ${createJson(graph)}"
    )

  test("processSubtree should detect cycles and truncate"):
    val expected = ModuleModel(
      "orgC:nameC:3.0",
      Vector(
        ModuleModel(
          "orgA:nameA:1.0",
          Vector(
            ModuleModel("orgA:nameA:1.0 (cycle)", Vector()),
            ModuleModel("orgB:nameB:2.0", Vector())
          )
        )
      )
    )
    assert(TreeView.processSubtree(graph, Module(modC), Set()) == expected)
end TreeViewTest
