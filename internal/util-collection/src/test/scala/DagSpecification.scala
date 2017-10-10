/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

import org.scalacheck._
import Prop._

import scala.collection.mutable.HashSet

object DagSpecification extends Properties("Dag") {
  property("No repeated nodes") = forAll { (dag: TestDag) =>
    isSet(dag.topologicalSort)
  }
  property("Sort contains node") = forAll { (dag: TestDag) =>
    dag.topologicalSort.contains(dag)
  }
  property("Dependencies precede node") = forAll { (dag: TestDag) =>
    dependenciesPrecedeNodes(dag.topologicalSort)
  }

  implicit lazy val arbTestDag: Arbitrary[TestDag] = Arbitrary(Gen.sized(dagGen))
  private def dagGen(nodeCount: Int): Gen[TestDag] = {
    val nodes = new HashSet[TestDag]
    def nonterminalGen(p: Gen.Parameters): Gen[TestDag] = {
      val seed = rng.Seed.random()
      for {
        i <- 0 until nodeCount
        nextDeps <- Gen.someOf(nodes).apply(p, seed)
      } nodes += new TestDag(i, nextDeps)
      for (nextDeps <- Gen.someOf(nodes)) yield new TestDag(nodeCount, nextDeps)
    }
    Gen.parameterized(nonterminalGen)
  }

  private def isSet[T](c: Seq[T]) = Set(c: _*).size == c.size
  private def dependenciesPrecedeNodes(sort: List[TestDag]) = {
    val seen = new HashSet[TestDag]
    def iterate(remaining: List[TestDag]): Boolean = {
      remaining match {
        case Nil => true
        case node :: tail =>
          if (node.dependencies.forall(seen.contains) && !seen.contains(node)) {
            seen += node
            iterate(tail)
          } else
            false
      }
    }
    iterate(sort)
  }
}
class TestDag(id: Int, val dependencies: Iterable[TestDag]) extends Dag[TestDag] {
  override def toString = id + "->" + dependencies.mkString("[", ",", "]")
}
