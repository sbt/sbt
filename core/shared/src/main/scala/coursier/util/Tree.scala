package coursier.util

import scala.collection.mutable.ArrayBuffer

object Tree {

  def apply[T](roots: IndexedSeq[T])(children: T => Seq[T], print: T => String): String = {

    def helper(elems: Seq[T], prefix: String, acc: String => Unit): Unit =
      for ((elem, idx) <- elems.zipWithIndex) {
        val isLast = idx == elems.length - 1

        val tee = if (isLast) "└─ " else "├─ "

        acc(prefix + tee + print(elem))

        val children0 = children(elem)

        if (children0.nonEmpty) {
          val extraPrefix = if (isLast) "   " else "|  "
          helper(children0, prefix + extraPrefix, acc)
        }
      }

    val b = new ArrayBuffer[String]
    helper(roots, "", b += _)
    b.mkString("\n")
  }

}
