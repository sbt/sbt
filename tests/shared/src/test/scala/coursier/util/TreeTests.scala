package coursier.util

import utest._

import scala.collection.mutable.ArrayBuffer

object TreeTests extends TestSuite {

  case class Node(label: String, children: ArrayBuffer[Node]) {
    def addChild(x: Node): Unit = {
      children.append(x)
    }

    // The default behavior of hashcode will calculate things recursively,
    // which will be infinite because we want to test cycles, so hardcoding
    // the hashcode to 0 to get around the issue.
    // TODO: make the hashcode to return something more interesting to
    // improve performance.
    override def hashCode(): Int = 0
  }

  val roots = Array(
    Node("p1", ArrayBuffer(
      Node("c1", ArrayBuffer.empty),
      Node("c2", ArrayBuffer.empty))),
    Node("p2", ArrayBuffer(
      Node("c3", ArrayBuffer.empty),
      Node("c4", ArrayBuffer.empty)))
  )

  val moreNestedRoots = Array(
    Node("p1", ArrayBuffer(
      Node("c1", ArrayBuffer(
        Node("p2", ArrayBuffer.empty))))),
    Node("p3", ArrayBuffer(
      Node("d1", ArrayBuffer.empty))
    ))


  // Constructing cyclic graph:
  // a -> b -> c -> a
  //             -> e -> f

  val a = Node("a", ArrayBuffer.empty)
  val b = Node("b", ArrayBuffer.empty)
  val c = Node("c", ArrayBuffer.empty)
  val e = Node("e", ArrayBuffer.empty)
  val f = Node("f", ArrayBuffer.empty)

  a.addChild(b)
  b.addChild(c)
  c.addChild(a)
  c.addChild(e)
  e.addChild(f)


  val tests = Tests {
    'basic {
      val str = Tree[Node](roots)(_.children, _.label)
      assert(str ==
        """├─ p1
          #│  ├─ c1
          #│  └─ c2
          #└─ p2
          #   ├─ c3
          #   └─ c4""".stripMargin('#'))
    }

    'moreNested {
      val str = Tree[Node](moreNestedRoots)(_.children, _.label)
      assert(str ==
        """├─ p1
          #│  └─ c1
          #│     └─ p2
          #└─ p3
          #   └─ d1""".stripMargin('#'))
    }

    'cyclic1 {
      val str: String = Tree[Node](Array(a, e))(_.children, _.label)
      assert(str ==
        """├─ a
          #│  └─ b
          #│     └─ c
          #│        └─ e
          #│           └─ f
          #└─ e
          #   └─ f""".stripMargin('#'))
    }

    'cyclic2 {
      val str: String = Tree[Node](Array(a, c))(_.children, _.label)
      assert(str ==
        """├─ a
          #│  └─ b
          #│     └─ c
          #│        └─ e
          #│           └─ f
          #└─ c
          #   ├─ a
          #   │  └─ b
          #   └─ e
          #      └─ f""".stripMargin('#'))
    }
  }
}
