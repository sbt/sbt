/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

trait Dag[Node <: Dag[Node]] { self: Node =>

  def dependencies: Iterable[Node]
  def topologicalSort = Dag.topologicalSort(self)(_.dependencies)
}
object Dag {
  import scala.collection.{ mutable, JavaConverters }
  import JavaConverters.asScalaSetConverter

  def topologicalSort[T](root: T)(dependencies: T => Iterable[T]): List[T] =
    topologicalSort(root :: Nil)(dependencies)

  def topologicalSort[T](nodes: Iterable[T])(dependencies: T => Iterable[T]): List[T] = {
    val discovered = new mutable.HashSet[T]
    val finished = (new java.util.LinkedHashSet[T]).asScala

    def visitAll(nodes: Iterable[T]) = nodes foreach visit
    def visit(node: T): Unit = {
      if (!discovered(node)) {
        discovered(node) = true;
        try { visitAll(dependencies(node)); } catch { case c: Cyclic => throw node :: c }
        finished += node
        ()
      } else if (!finished(node))
        throw new Cyclic(node)
    }

    visitAll(nodes)

    finished.toList
  }

  // doesn't check for cycles
  def topologicalSortUnchecked[T](node: T)(dependencies: T => Iterable[T]): List[T] =
    topologicalSortUnchecked(node :: Nil)(dependencies)

  def topologicalSortUnchecked[T](nodes: Iterable[T])(dependencies: T => Iterable[T]): List[T] = {
    val discovered = new mutable.HashSet[T]
    var finished: List[T] = Nil

    def visitAll(nodes: Iterable[T]) = nodes foreach visit
    def visit(node: T): Unit = {
      if (!discovered(node)) {
        discovered(node) = true
        visitAll(dependencies(node))
        finished ::= node
      }
    }

    visitAll(nodes);
    finished;
  }

  final class Cyclic(val value: Any, val all: List[Any], val complete: Boolean)
      extends Exception(
        "Cyclic reference involving " +
          (if (complete) all.mkString("\n   ", "\n   ", "") else value)
      ) {
    def this(value: Any) = this(value, value :: Nil, false)
    override def toString = getMessage

    def ::(a: Any): Cyclic =
      if (complete)
        this
      else if (a == value)
        new Cyclic(value, all, true)
      else
        new Cyclic(value, a :: all, false)
  }

  /** A directed graph with edges labeled positive or negative. */
  private[sbt] trait DirectedSignedGraph[Node] {

    /**
     * Directed edge type that tracks the sign and target (head) vertex.
     * The sign can be obtained via [[isNegative]] and the target vertex via [[head]].
     */
    type Arrow

    /** List of initial nodes. */
    def nodes: List[Arrow]

    /** Outgoing edges for `n`. */
    def dependencies(n: Node): List[Arrow]

    /** `true` if the edge `a` is "negative", false if it is "positive". */
    def isNegative(a: Arrow): Boolean

    /** The target of the directed edge `a`. */
    def head(a: Arrow): Node

  }

  /**
   * Traverses a directed graph defined by `graph` looking for a cycle that includes a "negative" edge.
   * The directed edges are weighted by the caller as "positive" or "negative".
   * If a cycle containing a "negative" edge is detected, its member edges are returned in order.
   * Otherwise, the empty list is returned.
   */
  private[sbt] def findNegativeCycle[Node](graph: DirectedSignedGraph[Node]): List[graph.Arrow] = {
    import graph._
    val finished = new mutable.HashSet[Node]
    val visited = new mutable.HashSet[Node]

    def visit(edges: List[Arrow], stack: List[Arrow]): List[Arrow] = edges match {
      case Nil => Nil
      case edge :: tail =>
        val node = head(edge)
        if (!visited(node)) {
          visited += node
          visit(dependencies(node), edge :: stack) match {
            case Nil =>
              finished += node
              visit(tail, stack)
            case cycle => cycle
          }
        } else if (!finished(node)) {
          // cycle. If a negative edge is involved, it is an error.
          val between = edge :: stack.takeWhile(f => head(f) != node)
          if (between exists isNegative)
            between
          else
            visit(tail, stack)
        } else
          visit(tail, stack)
    }

    visit(graph.nodes, Nil)
  }

}
