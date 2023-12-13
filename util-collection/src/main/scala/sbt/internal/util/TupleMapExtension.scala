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

    def unmap(f: [a] => F1[a] => a): Tup =
      Tuple.fromArray(tuple.iterator.map(f(_)).toArray).asInstanceOf[Tup]

    def transform[F2[_]](f: [a] => F1[a] => F2[a]): Tuple.Map[Tup, F2] =
      Tuple.fromArray(tuple.iterator.map(f(_)).toArray).asInstanceOf[Tuple.Map[Tup, F2]]

    def traverse[F2[_]](f: [a] => F1[a] => F2[a])(using app: Applicative[F2]): F2[Tup] =
      val fxs: F2[List[Any]] = tuple.iterator
        .foldRight[F2[List[Any]]](app.pure(() => Nil))((x, xs) =>
          app.map(app.product(f(x), xs))((h, t) => h :: t)
        )
      app.map(fxs)(xs => Tuple.fromArray(xs.toArray).asInstanceOf[Tup])

    def mapN[A1](f: Tup => A1)(using app: Applicative[F1]): F1[A1] =
      app.map(tuple.traverse[F1]([a] => (f: F1[a]) => f))(f)
end TupleMapExtension
