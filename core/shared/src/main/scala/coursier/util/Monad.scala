package coursier.util

import scala.language.higherKinds

trait Monad[F[_]] {
  def point[A](a: A): F[A]
  def bind[A, B](elem: F[A])(f: A => F[B]): F[B]

  def map[A, B](elem: F[A])(f: A => B): F[B] =
    bind(elem)(a => point(f(a)))
}
