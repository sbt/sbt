package coursier.util

import scala.collection.mutable.ListBuffer

object Traverse {

  implicit class TraverseOps[T](val seq: Seq[T]) {
    def eitherTraverse[L, R](f: T => Either[L, R]): Either[L, Seq[R]] =
      // Warning: iterates on the whole sequence no matter what, even if the first element is a Left
      seq.foldLeft[Either[L, ListBuffer[R]]](Right(new ListBuffer)) {
        case (l @ Left(_), _) => l
        case (Right(b), elem) =>
          f(elem) match {
            case Left(l) => Left(l)
            case Right(r) => Right(b += r)
          }
      }
  }

}
