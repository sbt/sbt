package coursier.util

import scala.language.higherKinds

trait Gather[F[_]] extends Monad[F] {
  def gather[A](elems: Seq[F[A]]): F[Seq[A]]
}

object Gather {
  def apply[F[_]](implicit G: Gather[F]): Gather[F] = G
}
