/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.graph.rendering

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sbt.internal.graph.rendering.TreeView.createJson
import sbt.internal.graph.{ Edge, GraphModuleId, Module, ModuleGraph, ModuleModel }

class TreeViewTest extends AnyFlatSpec with Matchers {
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

  "createJson" should "convert ModuleGraph into JSON correctly" in {
    val expected =
      "[{\"text\":\"orgC:nameC:3.0\",\"children\":[{\"text\":\"orgA:nameA:1.0\",\"children\":[{\"text\":\"orgA:nameA:1.0 (cycle)\",\"children\":[]},{\"text\":\"orgB:nameB:2.0\",\"children\":[]}]}]}]"
    Predef.assert(
      createJson(graph) == expected,
      s"Expected $expected, but got ${createJson(graph)}"
    )
  }

  "processSubtree" should "detect cycles and truncate" in {
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
  }

  // -- sbt/sbt#6886: DAG dedup --

  it should "collapse a duplicate subtree to a leaf marked with (*)" in {
    // a -> b -> c
    // a -> c     (c appears under two paths)
    val a = GraphModuleId("o", "a", "1")
    val b = GraphModuleId("o", "b", "1")
    val c = GraphModuleId("o", "c", "1")
    val d = GraphModuleId("o", "d", "1") // grandchild of c -- proves c's subtree only
    //                                     materializes once
    val g = ModuleGraph(
      nodes = Seq(Module(a), Module(b), Module(c), Module(d)),
      edges = Seq(a -> b, a -> c, b -> c, c -> d)
    )

    val tree = TreeView.processSubtree(g, Module(a))

    val expected = ModuleModel(
      "o:a:1",
      Vector(
        ModuleModel(
          "o:b:1",
          Vector(
            ModuleModel("o:c:1", Vector(ModuleModel("o:d:1", Vector())))
          )
        ),
        // second occurrence collapsed, no children
        ModuleModel("o:c:1 (*)", Vector())
      )
    )
    assert(tree == expected, s"got: $tree")
  }

  it should "render output linear in DAG node count for an exponential graph" in {
    // 8 nodes, each pointing to all later ones. Without dedup the JSON
    // would be ~2^7 = 128 inner ModuleModels; with dedup it should be
    // strictly bounded by the DAG node count (8) plus the duplicate
    // collapsed leaves.
    val nodes = ('a' to 'h').map(c => GraphModuleId("o", c.toString, "1")).toVector
    val edges: Seq[Edge] = for
      i <- nodes.indices
      j <- (i + 1) until nodes.size
    yield nodes(i) -> nodes(j)
    val g = ModuleGraph(nodes = nodes.map(Module(_)), edges = edges)

    // Helper: count *unique* texts emitted (each module rendered once
    // fully) and total ModuleModel count (with collapsed duplicates).
    def walk(m: ModuleModel): (Set[String], Int) =
      m.children.foldLeft((Set(m.text), 1)) { case ((seen, n), child) =>
        val (s, k) = walk(child)
        (seen ++ s, n + k)
      }
    val (texts, total) = walk(TreeView.processSubtree(g, Module(nodes.head)))

    // Every module appears as a non-duplicate text exactly once.
    nodes.foreach(n => assert(texts.contains(n.idString), s"missing $n in texts: $texts"))
    // Without dedup, total would be 2^7 = 128. With dedup, total is the
    // sum 1 + 7 + 6 + 5 + 4 + 3 + 2 + 1 = 29 (node `a`'s full subtree
    // plus a duplicate leaf for every other path).
    assert(total < 50, s"DAG rendering exploded: $total nodes")
  }

  it should "concatenate marker suffixes in a stable order (eviction, then (*))" in {
    // A module that's both evicted *and* shows up twice in the DAG.
    // Pins the suffix order so any future renderer refactor can't
    // silently change `(evicted by 2.0) (*)` to `(*) (evicted by 2.0)`
    // for callers parsing these lines.
    val a = GraphModuleId("o", "a", "1")
    val b = GraphModuleId("o", "b", "1")
    val c = GraphModuleId("o", "c", "1")
    val evicted = Module(c, evictedByVersion = Some("2.0"))
    val g = ModuleGraph(
      nodes = Seq(Module(a), Module(b), evicted),
      edges = Seq(a -> b, a -> c, b -> c)
    )
    val tree = TreeView.processSubtree(g, Module(a))
    // First occurrence (under b): "o:c:1 (evicted by 2.0)"; collapsed
    // occurrence (direct child of a): "o:c:1 (evicted by 2.0) (*)".
    val texts = collectTexts(tree)
    assert(
      texts.contains("o:c:1 (evicted by 2.0)"),
      s"missing first-occurrence text; got: $texts"
    )
    assert(
      texts.contains("o:c:1 (evicted by 2.0) (*)"),
      s"missing collapsed-occurrence text; got: $texts"
    )
  }

  private def collectTexts(m: ModuleModel): Set[String] =
    m.children.foldLeft(Set(m.text))((s, c) => s ++ collectTexts(c))

  it should "report a cycle differently than a duplicate" in {
    // a -> b -> a (cycle)
    // a -> c (no cycle, no duplicate)
    val a = GraphModuleId("o", "a", "1")
    val b = GraphModuleId("o", "b", "1")
    val c = GraphModuleId("o", "c", "1")
    val g = ModuleGraph(
      nodes = Seq(Module(a), Module(b), Module(c)),
      edges = Seq(a -> b, b -> a, a -> c)
    )
    val tree = TreeView.processSubtree(g, Module(a))
    val expected = ModuleModel(
      "o:a:1",
      Vector(
        ModuleModel(
          "o:b:1",
          Vector(ModuleModel("o:a:1 (cycle)", Vector()))
        ),
        ModuleModel("o:c:1", Vector())
      )
    )
    assert(tree == expected, s"got: $tree")
  }
}
