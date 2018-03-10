package coursier.util

import scala.language.higherKinds

final case class EitherT[F[_], L, R](run: F[Either[L, R]]) {

  def map[S](f: R => S)(implicit M: Monad[F]): EitherT[F, L, S] =
    EitherT(
      M.map(run)(e => e.right.map(f))
    )

  def flatMap[S](f: R => EitherT[F, L, S])(implicit M: Monad[F]): EitherT[F, L, S] =
    EitherT(
      M.bind(run) {
        case Left(l) =>
          M.point(Left(l))
        case Right(r) =>
          f(r).run
      }
    )

  def leftMap[M](f: L => M)(implicit M: Monad[F]): EitherT[F, M, R] =
    EitherT(
      M.map(run)(e => e.left.map(f))
    )

  def leftFlatMap[S](f: L => EitherT[F, S, R])(implicit M: Monad[F]): EitherT[F, S, R] =
    EitherT(
      M.bind(run) {
        case Left(l) =>
          f(l).run
        case Right(r) =>
          M.point(Right(r))
      }
    )

  def orElse(other: => EitherT[F, L, R])(implicit M: Monad[F]): EitherT[F, L, R] =
    EitherT(
      M.bind(run) {
        case Left(_) =>
          other.run
        case Right(r) =>
          M.point(Right(r))
      }
    )

  def scalaz(implicit M: Monad[F]): _root_.scalaz.EitherT[F, L, R] =
    _root_.scalaz.EitherT(
      M.map(run)(_root_.scalaz.\/.fromEither)
    )

}

object EitherT {

  def point[F[_], L, R](r: R)(implicit M: Monad[F]): EitherT[F, L, R] =
    EitherT(M.point(Right(r)))

  def fromEither[F[_]]: FromEither[F] =
    new FromEither[F]

  final class FromEither[F[_]] {
    def apply[L, R](either: Either[L, R])(implicit M: Monad[F]): EitherT[F, L, R] =
      EitherT(M.point(either))
  }

}
