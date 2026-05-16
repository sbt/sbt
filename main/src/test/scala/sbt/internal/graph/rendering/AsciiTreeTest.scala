/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.graph.rendering

import sbt.internal.Graph
import verify.BasicTestSuite

object AsciiTreeTest extends BasicTestSuite:

  private def render(adj: Map[Int, Seq[Int]], root: Int, width: Int = 80): String =
    Graph.toAscii[Int](root, n => adj.getOrElse(n, Nil), _.toString, width)

  test("strict tree renders without markers") {
    val adj = Map(0 -> Seq(1, 2), 1 -> Seq(3))
    val out = render(adj, 0)
    assert(!out.contains("(*)"), s"unexpected (*):\n$out")
    assert(!out.contains("(cycle)"), s"unexpected (cycle):\n$out")
    assert(out.contains("3"), s"node 3 missing:\n$out")
  }

  test("diamond DAG collapses the duplicate subtree to a single (*) line") {
    //   0
    //  / \
    // 1   2
    //  \ /
    //   3
    val adj = Map(0 -> Seq(1, 2), 1 -> Seq(3), 2 -> Seq(3))
    val out = render(adj, 0)
    assert(out.contains("3 (*)"), s"missing collapsed `3 (*)`:\n$out")
    val nonDup = out.linesIterator.filter(l => l.contains("3") && !l.contains("(*)")).toList
    assert(nonDup.size == 1, s"node 3 must render exactly once in non-collapsed form: $nonDup")
  }

  test("cycle uses (cycle), not (*)") {
    val adj = Map(0 -> Seq(1), 1 -> Seq(0))
    val out = render(adj, 0)
    assert(out.contains("(cycle)"), s"missing (cycle):\n$out")
    assert(!out.contains("(*)"), s"unexpected (*):\n$out")
  }

  test("canonical occurrence is the first-visited in DFS order") {
    // top -> [a, b]; a -> [b]. DFS visits `a` first and expands `b`
    // under it; `b` as `top`'s direct second child must collapse.
    val adj = Map(0 -> Seq(1, 2), 1 -> Seq(2))
    val out = render(adj, 0)
    val lines = out.linesIterator.toVector
    val firstTwo = lines.indexWhere(l => l.contains("2") && !l.contains("(*)"))
    val collapsedTwo = lines.indexWhere(l => l.contains("2 (*)"))
    assert(firstTwo >= 0, s"non-duplicate `2` missing:\n$out")
    assert(collapsedTwo >= 0, s"collapsed `2 (*)` missing:\n$out")
    assert(
      firstTwo < collapsedTwo,
      s"non-duplicate `2` must render before `(*)` copy (got $firstTwo < $collapsedTwo):\n$out"
    )
  }

  test("exponential DAG renders linear-sized output") {
    // 12 nodes; each i depends on every j > i. Without dedup the
    // output is O(2^11) lines.
    val n = 12
    val adj: Map[Int, Seq[Int]] =
      (0 until n).map(i => i -> ((i + 1) until n).toVector).toMap
    val out = render(adj, 0, width = 200)
    val lineCount = out.linesIterator.size
    assert(lineCount < 200, s"DAG render exploded to $lineCount lines")
    (0 until n).foreach { i =>
      val nonDup = out.linesIterator.exists(l => l.contains(s"$i") && !l.contains("(*)"))
      assert(nonDup, s"node $i never rendered in non-collapsed form")
    }
  }

end AsciiTreeTest
