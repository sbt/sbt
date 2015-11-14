package net.virtualvoid.sbt.graph.util

import org.specs2.mutable.Specification

class AsciiTreeLayoutSpecs extends Specification {
  sealed trait Tree
  case class Branch(left: Tree, right: Tree) extends Tree
  case class Leaf(i: Int) extends Tree

  def children(t: Tree): Seq[Tree] = t match {
    case Branch(left, right) => Seq(left, right)
    case _: Leaf => Nil
  }
  def display(t: Tree): String = t match {
    case Branch(left, right) => "Branch"
    case Leaf(value) => value.toString * value
  }

  "Graph" should {
    "layout simple graphs" in {
      val simple = Branch(Branch(Leaf(1), Leaf(2)), Leaf(3))
      AsciiTreeLayout.toAscii(simple, children, display, 20) ===
        """Branch
          |  +-Branch
          |  | +-1
          |  | +-22
          |  |\u0020
          |  +-333
          |  """.stripMargin
    }
    "layout deep graphs" in {
      val simple = Branch(Branch(Branch(Branch(Branch(Branch(Leaf(1), Leaf(1)), Leaf(1)), Leaf(1)), Leaf(2)), Leaf(3)), Leaf(4))
      AsciiTreeLayout.toAscii(simple, children, display, 10) ===
        """Branch
          |  +-Branch
          |  | +-Br..
          |  | | +-..
          |  | | | ..
          |  | | | ..
          |  | | | ..
          |  | | | ..
          |  | | | | |\u0020
          |  | | | ..
          |  | | | |\u0020
          |  | | | ..
          |  | | |\u0020
          |  | | +-22
          |  | |\u0020
          |  | +-333
          |  |\u0020
          |  +-4444
          |  """.stripMargin
    }
  }
}
