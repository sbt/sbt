package coursier.util

// not covariant because scala.:: isn't (and is there a point in being covariant in R but not L?)
final case class ValidationNel[L, R](either: Either[::[L], R]) {
  def isSuccess: Boolean =
    either.isRight
  def map[S](f: R => S): ValidationNel[L, S] =
    ValidationNel(either.right.map(f))
}

object ValidationNel {
  def fromEither[L, R](either: Either[L, R]): ValidationNel[L, R] =
    ValidationNel(either.left.map(l => ::(l, Nil)))
  def success[L]: SuccessBuilder[L] =
    new SuccessBuilder
  def failure[R]: FailureBuilder[R] =
    new FailureBuilder

  final class SuccessBuilder[L] {
    def apply[R](r: R): ValidationNel[L, R] =
      ValidationNel(Right(r))
  }
  final class FailureBuilder[R] {
    def apply[L](l: L): ValidationNel[L, R] =
      ValidationNel(Left(::(l, Nil)))
  }
}
