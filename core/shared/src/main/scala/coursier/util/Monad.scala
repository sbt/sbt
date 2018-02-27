package coursier.util

import scala.language.higherKinds

trait Monad[F[_]] {
  def point[A](a: A): F[A]
  def bind[A, B](elem: F[A])(f: A => F[B]): F[B]

  def map[A, B](elem: F[A])(f: A => B): F[B] =
    bind(elem)(a => point(f(a)))
}

object Monad {

  implicit def fromScalaz[F[_]](implicit M: scalaz.Monad[F]): Monad[F] =
    new Monad[F] {
      def point[A](a: A) = M.pure(a)
      def bind[A, B](elem: F[A])(f: A => F[B]) = M.bind(elem)(f)
    }

}
