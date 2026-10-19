/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.graph.rendering

import hedgehog.*
import hedgehog.runner.*
import _root_.sbt.internal.graph.rendering.TreeView.createJson
import _root_.sbt.internal.graph.{ Edge, GraphModuleId, Module, ModuleGraph, ModuleModel }

object TreeViewTest extends Properties:
  override def tests: List[Test] = List(
    example("createJson should convert ModuleGraph into JSON correctly", testCreateJson),
    example("processSubtree should detect cycles and truncate", testCycles),
    example(
      "processSubtree should collapse a duplicate subtree to a leaf marked with (*) (#6886)",
      testCollapseDuplicate
    ),
    property(
      "processSubtree should render output linear in DAG node count for an exponential graph",
      propCompleteDag
    ),
    example(
      "processSubtree should concatenate marker suffixes in a stable order (eviction, then (*))",
      testSuffixOrder
    ),
    example(
      "processSubtree should report a cycle differently than a duplicate",
      testCycleVsDuplicate
    ),
  )

  val modA = GraphModuleId("orgA", "nameA", "1.0")
  val modB = GraphModuleId("orgB", "nameB", "2.0")
  val modC = GraphModuleId("orgC", "nameC", "3.0")

  val graph = ModuleGraph(
    nodes = Seq(Module(modA), Module(modB), Module(modC)),
    edges = Seq(
      modA -> modA,
      modA -> modB,
      modC -> modA,
    )
  )

  def testCreateJson: Result =
    createJson(graph) ====
      "[{\"text\":\"orgC:nameC:3.0\",\"children\":[{\"text\":\"orgA:nameA:1.0\",\"children\":[{\"text\":\"orgA:nameA:1.0 (cycle)\",\"children\":[]},{\"text\":\"orgB:nameB:2.0\",\"children\":[]}]}]}]"

  def testCycles: Result =
    TreeView.processSubtree(graph, Module(modC), Set()) ====
      ModuleModel(
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

  def testCollapseDuplicate: Result =
    val a = GraphModuleId("o", "a", "1")
    val b = GraphModuleId("o", "b", "1")
    val c = GraphModuleId("o", "c", "1")
    val d = GraphModuleId("o", "d", "1")
    val g = ModuleGraph(
      nodes = Seq(Module(a), Module(b), Module(c), Module(d)),
      edges = Seq(a -> b, a -> c, b -> c, c -> d)
    )
    TreeView.processSubtree(g, Module(a)) ====
      ModuleModel(
        "o:a:1",
        Vector(
          ModuleModel(
            "o:b:1",
            Vector(
              ModuleModel("o:c:1", Vector(ModuleModel("o:d:1", Vector())))
            )
          ),
          ModuleModel("o:c:1 (*)", Vector())
        )
      )
  end testCollapseDuplicate

  /**
   * Every node points to all later ones, so without dedup the tree has 2^(n-1) entries. With dedup
   * each node is expanded once and every other edge is a collapsed leaf: 1 + n(n-1)/2.
   */
  def propCompleteDag: Property =
    for n <- Gen.int(Range.linear(1, 10)).forAll
    yield
      val nodes = (0 until n).map(i => GraphModuleId("o", s"m$i", "1")).toVector
      val edges: Seq[Edge] =
        for
          i <- nodes.indices
          j <- (i + 1) until nodes.size
        yield nodes(i) -> nodes(j)
      val g = ModuleGraph(nodes = nodes.map(Module(_)), edges = edges)
      val tree = TreeView.processSubtree(g, Module(nodes.head))
      val texts = collectTexts(tree)
      Result.all(
        (count(tree) ==== 1 + n * (n - 1) / 2) ::
          nodes.toList.map(m =>
            Result.assert(texts.contains(m.idString)).log(s"missing $m in texts: $texts")
          )
      )

  def testSuffixOrder: Result =
    val a = GraphModuleId("o", "a", "1")
    val b = GraphModuleId("o", "b", "1")
    val c = GraphModuleId("o", "c", "1")
    val evicted = Module(c, evictedByVersion = Some("2.0"))
    val g = ModuleGraph(
      nodes = Seq(Module(a), Module(b), evicted),
      edges = Seq(a -> b, a -> c, b -> c)
    )
    val texts = collectTexts(TreeView.processSubtree(g, Module(a)))
    Result.all(
      List(
        Result
          .assert(texts.contains("o:c:1 (evicted by 2.0)"))
          .log(s"missing first-occurrence text; got: $texts"),
        Result
          .assert(texts.contains("o:c:1 (evicted by 2.0) (*)"))
          .log(s"missing collapsed-occurrence text; got: $texts"),
      )
    )

  def testCycleVsDuplicate: Result =
    val a = GraphModuleId("o", "a", "1")
    val b = GraphModuleId("o", "b", "1")
    val c = GraphModuleId("o", "c", "1")
    val g = ModuleGraph(
      nodes = Seq(Module(a), Module(b), Module(c)),
      edges = Seq(a -> b, b -> a, a -> c)
    )
    TreeView.processSubtree(g, Module(a)) ====
      ModuleModel(
        "o:a:1",
        Vector(
          ModuleModel(
            "o:b:1",
            Vector(ModuleModel("o:a:1 (cycle)", Vector()))
          ),
          ModuleModel("o:c:1", Vector())
        )
      )

  private def collectTexts(m: ModuleModel): Set[String] =
    m.children.foldLeft(Set(m.text))((s, c) => s ++ collectTexts(c))

  private def count(m: ModuleModel): Int =
    1 + m.children.map(count).sum
end TreeViewTest
