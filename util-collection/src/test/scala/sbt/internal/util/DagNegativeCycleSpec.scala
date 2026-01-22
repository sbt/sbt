/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import verify.BasicTestSuite
import scala.collection.mutable

object DagNegativeCycleSpec extends BasicTestSuite:
  import Dag.*

  test("findNegativeCycle should correctly detect negative cycles even when node position in stack is unexpected") {
    // Create a graph with a cycle that has a negative edge
    // This test case reproduces an edge case where the original takeWhile
    // logic might fail to correctly identify the cycle
    case class TestNode(id: String)
    case class TestArrow(from: String, to: String, negative: Boolean)

    val graph = new DirectedSignedGraph[TestNode] {
      type Arrow = TestArrow

      // Create a cycle: A -> B -> C -> A, where B -> C is negative
      val nodeA = TestNode("A")
      val nodeB = TestNode("B")
      val nodeC = TestNode("C")

      val arrowAB = TestArrow("A", "B", negative = false)
      val arrowBC = TestArrow("B", "C", negative = true)  // negative edge
      val arrowCA = TestArrow("C", "A", negative = false)

      def nodes: List[Arrow] = List(arrowAB)

      def dependencies(n: TestNode): List[Arrow] = n match {
        case TestNode("A") => List(arrowAB)
        case TestNode("B") => List(arrowBC)
        case TestNode("C") => List(arrowCA)
        case _ => Nil
      }

      def isNegative(a: Arrow): Boolean = a.negative
      def head(a: Arrow): TestNode = TestNode(a.to)
    }

    val cycle = Dag.findNegativeCycle(graph)
    
    // The cycle should be detected and should include the negative edge
    assert(cycle.nonEmpty, "Cycle should not be empty")
    assert(cycle.exists(_.negative), "Cycle should contain negative edge")
    // Verify the cycle contains the expected edges
    val cycleIds = cycle.map(a => s"${a.from}->${a.to}")
    assert(cycleIds.contains("B->C"), s"The negative edge B->C must be in the cycle. Found: ${cycleIds.mkString(", ")}")
  }

  test("findNegativeCycle should handle cycles where the node appears multiple times in traversal path") {
    // This test case specifically targets the edge case where
    // stack.takeWhile might not correctly identify the cycle start
    case class TestNode(id: String)
    case class TestArrow(from: String, to: String, negative: Boolean)

    val graph = new DirectedSignedGraph[TestNode] {
      type Arrow = TestArrow

      // Create a more complex cycle: A -> B -> C -> D -> B (cycle back to B)
      // where C -> D is negative
      val arrowAB = TestArrow("A", "B", negative = false)
      val arrowBC = TestArrow("B", "C", negative = false)
      val arrowCD = TestArrow("C", "D", negative = true)  // negative edge
      val arrowDB = TestArrow("D", "B", negative = false)

      def nodes: List[Arrow] = List(arrowAB)

      def dependencies(n: TestNode): List[Arrow] = n match {
        case TestNode("A") => List(arrowAB)
        case TestNode("B") => List(arrowBC)
        case TestNode("C") => List(arrowCD)
        case TestNode("D") => List(arrowDB)
        case _ => Nil
      }

      def isNegative(a: Arrow): Boolean = a.negative
      def head(a: Arrow): TestNode = TestNode(a.to)
    }

    val cycle = Dag.findNegativeCycle(graph)
    
    // The negative cycle should be detected
    assert(cycle.nonEmpty, "Cycle should not be empty")
    assert(cycle.exists(_.negative), "Cycle should contain negative edge")
    // The cycle should include the negative edge C->D
    assert(cycle.exists(a => a.from == "C" && a.to == "D"), s"Cycle should include negative edge C->D. Found: ${cycle.map(a => s"${a.from}->${a.to}").mkString(", ")}")
  }
end DagNegativeCycleSpec

