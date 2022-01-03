/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal
package util

import sbt.util.Applicative
import Types._

/**
 * Arity-generic List. An abstraction over structured Tuple type constructor `X1[f[a]]`.
 */
trait AList[K[F[_]]]:
  import AList.idPoly

  def transform[F1[_], F2[_]](value: K[F1])(
      f: [a] => F1[a] => F2[a]
  ): K[F2]

  def traverse[F1[_], F2[_]: Applicative](value: K[F1])(
      f: [a] => F1[a] => F2[a]
  ): F2[K[Id]]

  def mapN[F1[_]: Applicative, A1](value: K[F1])(f: K[Id] => A1): F1[A1] =
    summon[Applicative[F1]].map(traverse[F1, F1](value)(idPoly[F1]))(f)

  def traverseX[F1[_], F2[_]: Applicative, P[_]](value: K[F1])(
      f: [a] => F1[a] => F2[P[a]]
  ): F2[K[P]]

  def foldr[F1[_], A1](value: K[F1], init: A1)(
      f: [a] => (F1[a], A1) => A1
  ): A1

  def toList[F1[_]](value: K[F1]): List[F1[Any]] =
    val f = [a] => (p1: F1[a], p2: List[F1[Any]]) => p1.asInstanceOf[F1[Any]] :: p2
    foldr[F1, List[F1[Any]]](value, Nil)(f)
end AList

object AList:
  type Tail[X <: Tuple] <: Tuple = X match
    case _ *: xs => xs

  def idPoly[F1[_]] = [a] => (p: F1[a]) => p

  def nil[Tup <: Tuple] = EmptyTuple.asInstanceOf[Tup]

  inline def toTuple[A](a: A): Tuple1[A] = Tuple1(a)

  inline def fromTuple[A1, A2](f: A1 => A2): Tuple1[A1] => A2 = { case Tuple1(a) => f(a) }

  // givens for tuple map
  given [Tup <: Tuple]: AList[[F[_]] =>> Tuple.Map[Tup, F]] = tuple[Tup]

  def tuple[Tup <: Tuple]: AList[[F[_]] =>> Tuple.Map[Tup, F]] =
    new AList[[F[_]] =>> Tuple.Map[Tup, F]]:
      override def transform[F1[_], F2[_]](value: Tuple.Map[Tup, F1])(
          f: [x] => F1[x] => F2[x]
      ): Tuple.Map[Tup, F2] =
        value match
          case _: Tuple.Map[EmptyTuple, F1] => nil[Tuple.Map[Tup, F2]]
          case (head: F1[x] @unchecked) *: tail =>
            (f(head) *: transform[F1, F2](tail.asInstanceOf)(f))
              .asInstanceOf[Tuple.Map[Tup, F2]]

      override def traverse[F1[_], F2[_]: Applicative](value: Tuple.Map[Tup, F1])(
          f: [a] => F1[a] => F2[a]
      ): F2[Tuple.Map[Tup, Id]] =
        val F2 = summon[Applicative[F2]]
        value match
          case _: Tuple.Map[EmptyTuple, F1] => F2.pure(nil[Tup].asInstanceOf[Tuple.Map[Tup, Id]])
          case (head: F1[x] @unchecked) *: (tail: Tuple.Map[Tail[Tup], F1] @unchecked) =>
            val tt = tuple[Tail[Tup]].traverse[F1, F2](tail)(f)
            val g = (t: Tail[Tup]) => (h: x) => (h *: t)
            F2.ap[x, Tup](F2.map(tt)(g.asInstanceOf))(f(head)).asInstanceOf[F2[Tuple.Map[Tup, Id]]]

      override def traverseX[F1[_], F2[_]: Applicative, P[_]](
          value: Tuple.Map[Tup, F1]
      )(
          f: [a] => F1[a] => F2[P[a]]
      ): F2[Tuple.Map[Tup, P]] =
        val F2 = summon[Applicative[F2]]
        value match
          case _: Tuple.Map[EmptyTuple, F1] => F2.pure(nil[Tuple.Map[Tup, P]])
          case (head: F1[x] @unchecked) *: (tail: Tuple.Map[Tail[Tup], F1] @unchecked) =>
            val tt = traverseX[F1, F2, P](tail.asInstanceOf)(f)
            val g = (t: Tuple.Map[Tail[Tup], P]) =>
              (h: P[x]) => (h *: t).asInstanceOf[Tuple.Map[Tup, P]]
            F2.ap[P[x], Tuple.Map[Tup, P]](F2.map(tt)(g.asInstanceOf))(f(head))

      override def foldr[F1[_], A1](value: Tuple.Map[Tup, F1], init: A1)(
          f: [a] => (F1[a], A1) => A1
      ): A1 =
        value match
          case _: Tuple.Map[EmptyTuple, F1] => init
          case (head: F1[x] @unchecked) *: tail =>
            f(head, foldr[F1, A1](tail.asInstanceOf, init)(f))
end AList
