package net.virtualvoid.sbt.graph.util

import org.specs2.mutable.Specification

class AsciiTreeLayoutSpecs extends Specification {
  sealed trait Tree
  case class Branch(left: Tree, right: Tree) extends Tree
  case class Leaf(i: Int) extends Tree

  def children(t: Tree): Seq[Tree] = t match {
    case Branch(left, right) ⇒ Seq(left, right)
    case _: Leaf             ⇒ Nil
  }
  def display(t: Tree): String = t match {
    case Branch(left, right) ⇒ "Branch"
    case Leaf(value)         ⇒ value.toString * value
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
    "add separator lines where applicable" in {
      val simple = Branch(Branch(Leaf(1), Branch(Leaf(2), Leaf(3))), Leaf(4))
      AsciiTreeLayout.toAscii(simple, children, display, 20) ===
        """Branch
          |  +-Branch
          |  | +-1
          |  | +-Branch
          |  |   +-22
          |  |   +-333
          |  |\u0020\u0020\u0020
          |  +-4444
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
    "cut off cycles" in {
      AsciiTreeLayout.toAscii[Int](1, Map(
        1 -> Seq(2, 3, 4),
        2 -> Seq(4, 5),
        3 -> Seq(),
        4 -> Seq(3),
        5 -> Seq(1, 4, 6, 7),
        6 -> Seq(),
        7 -> Seq()), _.toString).trim ===
        """1
          |  +-2
          |  | +-4
          |  | | +-3
          |  | |\u0020
          |  | +-5
          |  |   #-1 (cycle)
          |  |   +-4
          |  |   | +-3
          |  |   |\u0020
          |  |   +-6
          |  |   +-7
          |  |\u0020\u0020\u0020
          |  +-3
          |  +-4
          |    +-3""".stripMargin.trim
    }
  }
}
