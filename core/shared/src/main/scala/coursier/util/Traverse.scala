package coursier.util

import scala.collection.mutable.ListBuffer

object Traverse {

  implicit class TraverseOps[T](val seq: Seq[T]) {

    def eitherTraverse[L, R](f: T => Either[L, R]): Either[L, Seq[R]] =
      // Warning: iterates on the whole sequence no matter what, even if the first element is a Left
      seq
        .foldLeft[Either[L, ListBuffer[R]]](Right(new ListBuffer)) {
          case (l @ Left(_), _) => l
          case (Right(b), elem) =>
            f(elem) match {
              case Left(l) => Left(l)
              case Right(r) => Right(b += r)
            }
        }
        .right
        .map(_.result())

    def validationNelTraverse[L, R](f: T => ValidationNel[L, R]): ValidationNel[L, Seq[R]] = {

      val e = seq
        .foldLeft[Either[ListBuffer[L], ListBuffer[R]]](Right(new ListBuffer)) {
          case (l @ Left(b), elem) =>
            f(elem).either match {
              case Left(l0) => Left(b ++= l0)
              case Right(_) => l
            }
          case (Right(b), elem) =>
            f(elem).either match {
              case Left(l) => Left(new ListBuffer[L] ++= l)
              case Right(r) => Right(b += r)
            }
        }
        .left
        .map { b =>
          b.result() match {
            case Nil => sys.error("Can't happen")
            case h :: t => ::(h, t)
          }
        }
        .right
        .map(_.result())

      ValidationNel(e)
    }

  }

}
