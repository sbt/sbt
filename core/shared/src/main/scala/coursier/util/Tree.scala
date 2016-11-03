package coursier.util

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object Tree {

  def apply[T](roots: IndexedSeq[T])(children: T => Seq[T], print: T => String): String = {
    /**
      * Add elements to the stack
      * @param stack a mutable stack which will have elements
      * @param elems elements to add
      * @param isLast a list that contains whether an element is the last in its siblings or not.
      *               The first element is the deepest, due to the fact prepending a List is faster than appending
      */
    def push(stack: mutable.Stack[(T, List[Boolean])], elems: Seq[T], isLast: List[Boolean]) = {
      // Reverse the list because the stack is LIFO but elems must be shown in the order
      for ((x, idx) <- elems.zipWithIndex.reverse) {
        stack.push((x, (idx == elems.length - 1) :: isLast))
      }
    }

    def prefix(isLast: List[Boolean]): String = {
      // Reverse the list because its first element is the deepest element
      isLast.reverse.zipWithIndex.map {
        case (last, idx) =>
          if (idx == isLast.length - 1)
            if (last) "└─ " else "├─ "
          else
            if (last) "   " else "|  "
      }.mkString("")
    }

    // Depth-first traverse
    def helper(elems: Seq[T], acc: String => Unit): Unit = {
      val stack = new mutable.Stack[(T, List[Boolean])]()
      val seen = new mutable.HashSet[T]()

      push(stack, elems, List[Boolean]())

      while (stack.nonEmpty) {
        val (elem, isLast) = stack.pop()
        acc(prefix(isLast) + print(elem))

        if (! seen.contains(elem)) {
          push(stack, children(elem), isLast)
          seen.add(elem)
        }
      }
    }

    val b = new ArrayBuffer[String]
    helper(roots, b += _)
    b.mkString("\n")
  }

}
