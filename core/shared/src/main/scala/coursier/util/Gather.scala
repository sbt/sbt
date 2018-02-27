package coursier.util

import scala.language.higherKinds

trait Gather[F[_]] extends Monad[F] {
  def gather[A](elems: Seq[F[A]]): F[Seq[A]]
}

object Gather {

  implicit def fromScalaz[F[_]](implicit N: scalaz.Nondeterminism[F]): Gather[F] =
    new Gather[F] {
      def point[A](a: A) = N.pure(a)
      def bind[A, B](elem: F[A])(f: A => F[B]) = N.bind(elem)(f)
      def gather[A](elems: Seq[F[A]]) = N.map(N.gather(elems))(l => l)
    }

}
