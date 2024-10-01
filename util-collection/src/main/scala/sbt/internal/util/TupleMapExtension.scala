/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal
package util

import sbt.util.Applicative

object TupleMapExtension:
  extension [Tup <: Tuple, F1[_]](tuple: Tuple.Map[Tup, F1])
    def iterator: Iterator[F1[Any]] = tuple.productIterator.asInstanceOf[Iterator[F1[Any]]]

    // typed version of tuple.toList
    def toList0: List[F1[Any]] =
      tuple.iterator.toList.asInstanceOf[List[F1[Any]]]

    def unmap(f: [a] => F1[a] => a): Tup = transform[[A] =>> A](f).asInstanceOf[Tup]
    def transform[F2[_]](f: [a] => F1[a] => F2[a]): Tuple.Map[Tup, F2] =
      inline def f0(x: Any) = f(x.asInstanceOf[F1[Any]])
      // We could use tuple.map from the scala3-library but it creates temporary arrays
      // which has an impact on the performance.
      // Instead, for small tuples, of size under 22, we map over each element manually.
      // format: off
      val res = (tuple: Tuple) match
        case EmptyTuple => EmptyTuple
        case t: NonEmptyTuple =>
          t.size match
            case 1 => Tuple1(f0(t(0)))
            case 2 => (f0(t(0)), f0(t(1)))
            case 3 => (f0(t(0)), f0(t(1)), f0(t(2)))
            case 4 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)))
            case 5 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)))
            case 6 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)))
            case 7 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)))
            case 8 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)), f0(t(7)))
            case 9 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)), f0(t(7)), f0(t(8)))
            case 10 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)), f0(t(7)), f0(t(8)), f0(t(9)))
            case 11 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)), f0(t(7)), f0(t(8)), f0(t(9)), f0(t(10)))
            case 12 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)), f0(t(7)), f0(t(8)), f0(t(9)), f0(t(10)), f0(t(11)))
            case 13 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)), f0(t(7)), f0(t(8)), f0(t(9)), f0(t(10)), f0(t(11)), f0(t(12)))
            case 14 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)), f0(t(7)), f0(t(8)), f0(t(9)), f0(t(10)), f0(t(11)), f0(t(12)), f0(t(13)))
            case 15 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)), f0(t(7)), f0(t(8)), f0(t(9)), f0(t(10)), f0(t(11)), f0(t(12)), f0(t(13)), f0(t(14)))
            case 16 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)), f0(t(7)), f0(t(8)), f0(t(9)), f0(t(10)), f0(t(11)), f0(t(12)), f0(t(13)), f0(t(14)), f0(t(15)))
            case 17 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)), f0(t(7)), f0(t(8)), f0(t(9)), f0(t(10)), f0(t(11)), f0(t(12)), f0(t(13)), f0(t(14)), f0(t(15)), f0(t(16)))
            case 18 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)), f0(t(7)), f0(t(8)), f0(t(9)), f0(t(10)), f0(t(11)), f0(t(12)), f0(t(13)), f0(t(14)), f0(t(15)), f0(t(16)), f0(t(17)))
            case 19 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)), f0(t(7)), f0(t(8)), f0(t(9)), f0(t(10)), f0(t(11)), f0(t(12)), f0(t(13)), f0(t(14)), f0(t(15)), f0(t(16)), f0(t(17)), f0(t(18)))
            case 20 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)), f0(t(7)), f0(t(8)), f0(t(9)), f0(t(10)), f0(t(11)), f0(t(12)), f0(t(13)), f0(t(14)), f0(t(15)), f0(t(16)), f0(t(17)), f0(t(18)), f0(t(19)))
            case 21 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)), f0(t(7)), f0(t(8)), f0(t(9)), f0(t(10)), f0(t(11)), f0(t(12)), f0(t(13)), f0(t(14)), f0(t(15)), f0(t(16)), f0(t(17)), f0(t(18)), f0(t(19)), f0(t(20)))
            case 22 => (f0(t(0)), f0(t(1)), f0(t(2)), f0(t(3)), f0(t(4)), f0(t(5)), f0(t(6)), f0(t(7)), f0(t(8)), f0(t(9)), f0(t(10)), f0(t(11)), f0(t(12)), f0(t(13)), f0(t(14)), f0(t(15)), f0(t(16)), f0(t(17)), f0(t(18)), f0(t(19)), f0(t(20)), f0(t(21)))
            case _ => scala.runtime.TupleXXL.fromIterator(tuple.iterator.map(f(_)))
      // format: on
      res.asInstanceOf[Tuple.Map[Tup, F2]]

    def traverse[F2[_]](f: [a] => F1[a] => F2[a])(using app: Applicative[F2]): F2[Tup] =
      val fxs: F2[List[Any]] = tuple.iterator
        .foldRight[F2[List[Any]]](app.pure(() => Nil))((x, xs) =>
          app.map(app.product(f(x), xs))((h, t) => h :: t)
        )
      app.map(fxs)(xs => Tuple.fromArray(xs.toArray).asInstanceOf[Tup])

    def mapN[A1](f: Tup => A1)(using app: Applicative[F1]): F1[A1] =
      app.map(tuple.traverse[F1]([a] => (f: F1[a]) => f))(f)
end TupleMapExtension
