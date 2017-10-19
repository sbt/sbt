package coursier.util

import scala.collection.mutable.ArrayBuffer

object Tree {

  def apply[T](roots: IndexedSeq[T])(children: T => Seq[T], show: T => String): String = {

    /**
      * Recursively go down the resolution for the elems to construct the tree for print out.
      *
      * @param elems     Seq of Elems that have been resolved
      * @param ancestors a set of Elems to keep track for cycle detection
      * @param prefix    prefix for the print out
      * @param acc       accumulation method on a string
      */
    def recursivePrint(elems: Seq[T], ancestors: Set[T], prefix: String, acc: String => Unit): Unit = {
      val unseenElems: Seq[T] = elems.filterNot(ancestors.contains)
      val unseenElemsLen = unseenElems.length
      for ((elem, idx) <- unseenElems.iterator.zipWithIndex) {
        val isLast = idx == unseenElemsLen - 1
        val tee = if (isLast) "└─ " else "├─ "
        acc(prefix + tee + show(elem))

        val extraPrefix = if (isLast) "   " else "│  "
        recursivePrint(children(elem), ancestors + elem, prefix + extraPrefix, acc)
      }
    }

    val b = new ArrayBuffer[String]
    recursivePrint(roots, Set(), "", b += _)
    b.mkString("\n")
  }

}
