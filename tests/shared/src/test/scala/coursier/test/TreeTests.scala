package coursier
package test

import coursier.util.{Tree, Xml}
import utest._

object TreeTests extends TestSuite {
  private def tree(str: String, xs: Array[Xml.Node] = Array()): Xml.Node = {
    new Xml.Node {
      override def label: String = str
      override def children = xs

      override def attributes: Seq[(String, String, String)] = Array[(String, String, String)]()
      override def isText: Boolean = false
      override def textContent: String = ""
      override def isElement: Boolean = true
    }
  }

  val roots = Array(
    tree("p1", Array(tree("c1"), tree("c2"))),
    tree("p2", Array(tree("c3"), tree("c4"))))

  val tests = TestSuite {
    'apply {
      val str = Tree(roots)(_.children, _.label)
      assert(str == """├─ p1
        ||  ├─ c1
        ||  └─ c2
        |└─ p2
        |   ├─ c3
        |   └─ c4""".stripMargin)
    }
  }
}
