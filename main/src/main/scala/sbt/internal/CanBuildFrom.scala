package sbt
package internal

import scala.annotation.implicitNotFound
import scala.collection.generic.{CanBuildFrom => CollCanBuildFrom}
import scala.collection.mutable.Builder

@implicitNotFound(msg = "Cannot construct a collection of type ${To} with elements of type ${B} based on a collection of type ${A}.")
sealed trait CanBuildFrom[-A, -B, +To] extends Any {
  private[internal] def canBuildFrom: CollCanBuildFrom[Iterable[A], B, To]
}

object CanBuildFrom {
  private[internal] case class NonEmptyCanBuildFrom[A, B](bf: CollCanBuildFrom[Iterable[A], B, Iterable[B]])
    extends AnyVal with CanBuildFrom[A, B, NonEmpty[B]] {

    private[internal] def canBuildFrom: CollCanBuildFrom[Iterable[A], B, NonEmpty[B]] =
      new CollCanBuildFrom[Iterable[A], B, NonEmpty[B]] {
        def apply(from: Iterable[A]): Builder[B, NonEmpty[B]] =
          bf.apply(from).mapResult(new NonEmpty(_))

        def apply(): Builder[B, NonEmpty[B]] =
         bf.apply().mapResult(new NonEmpty(_))
    }
  }

  private[internal] case class OtherCanBuildFrom[A, B](bf: CollCanBuildFrom[Iterable[A], B, NonEmpty[B]])
    extends AnyVal with CanBuildFrom[A, B, NonEmpty[B]] {

    private[internal] def canBuildFrom: CollCanBuildFrom[Iterable[A], B, NonEmpty[B]] = bf
  }

  implicit def nonEmptyCanBuildFrom[A, B](implicit bf: CollCanBuildFrom[Iterable[A], B, Iterable[B]]): NonEmptyCanBuildFrom[A, B] =
    NonEmptyCanBuildFrom(bf)
}
