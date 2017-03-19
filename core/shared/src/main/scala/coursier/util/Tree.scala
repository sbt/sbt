package coursier.util

import scala.annotation.tailrec

object Tree {

  def apply[T](roots: IndexedSeq[T])(children: T => Seq[T], show: T => String): String = {

    val buffer = new StringBuilder
    val printLine: (String) => Unit = { line =>
      buffer.append(line).append('\n')
    }

    def last[E, O](seq: Seq[E])(f: E => O) =
      seq.takeRight(1).map(f)
    def init[E, O](seq: Seq[E])(f: E => O) =
      seq.dropRight(1).map(f)

    /**
      * Add elements to the stack
      * @param elems elements to add
      * @param isLast a list that contains whether an element is the last in its siblings or not.
      */
    def childrenWithLast(elems: Seq[T],
                         isLast: Seq[Boolean]): Seq[(T, Seq[Boolean])] = {

      val isNotLast = isLast :+ false

      init(elems)(_ -> isNotLast) ++
        last(elems)(_ -> (isLast :+ true))
    }

    /**
      * Has to end with a "─"
      */
    def showLine(isLast: Seq[Boolean]): String = {
      val initPrefix = init(isLast) {
        case true => "   "
        case false => "|  "
      }.mkString

      val lastPrefix = last(isLast) {
        case true => "└─ "
        case false => "├─ "
      }.mkString

      initPrefix + lastPrefix
    }

    // Depth-first traverse
    @tailrec
    def helper(stack: Seq[(T, Seq[Boolean])], seen: Set[T]): Unit = {
      stack match {
        case (elem, isLast) +: next =>
          printLine(showLine(isLast) + show(elem))

          if (!seen(elem))
            helper(childrenWithLast(children(elem), isLast) ++ next,
                   seen + elem)
          else
            helper(next, seen)
        case Seq() =>
      }
    }

    helper(childrenWithLast(roots, Vector[Boolean]()), Set.empty)

    buffer
      .dropRight(1) // drop last appended '\n'
      .toString
  }

}
