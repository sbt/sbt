package coursier.interop

import coursier.util.{Gather, Monad}

object scalaz extends LowPriorityScalazImplicits {

  implicit def coursierMonadFromScalaz[F[_]](implicit M: _root_.scalaz.Monad[F]): Monad[F] =
    new Monad[F] {
      def point[A](a: A) = M.pure(a)
      def bind[A, B](elem: F[A])(f: A => F[B]) = M.bind(elem)(f)
    }

}

abstract class LowPriorityScalazImplicits extends PlatformScalazImplicits {

  implicit def coursierGatherFromScalaz[F[_]](implicit N: _root_.scalaz.Nondeterminism[F]): Gather[F] =
    new Gather[F] {
      def point[A](a: A) = N.pure(a)
      def bind[A, B](elem: F[A])(f: A => F[B]) = N.bind(elem)(f)
      def gather[A](elems: Seq[F[A]]) = N.map(N.gather(elems))(l => l)
    }

}
