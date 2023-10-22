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
 * Arity-generic List. An abstraction over structured Tuple/List type constructor `X1[f[a]]`.
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
  inline def apply[K[F[_]]: AList]: AList[K] = summon[AList[K]]

  type Tail[X <: Tuple] <: Tuple = X match
    case _ *: xs => xs

  def idPoly[F1[_]] = [a] => (p: F1[a]) => p

  def nil[Tup <: Tuple] = EmptyTuple.asInstanceOf[Tup]

  inline def toTuple[A](a: A): Tuple1[A] = Tuple1(a)

  inline def fromTuple[A1, A2](f: A1 => A2): Tuple1[A1] => A2 = { case Tuple1(a) => f(a) }

  // givens for tuple map
  given [Tup <: Tuple]: AList[[F[_]] =>> Tuple.Map[Tup, F]] = tuple[Tup]

  type Empty = AList[[F[_]] =>> Unit]

  lazy val empty: Empty = new Empty:
    override def transform[F1[_], F2[_]](value: Unit)(f: [x] => F1[x] => F2[x]): Unit = ()
    override def traverse[F1[_], F2[_]: Applicative](value: Unit)(
        f: [a] => F1[a] => F2[a]
    ): F2[Unit] = summon[Applicative[F2]].pure(() => ())
    override def traverseX[F1[_], F2[_]: Applicative, P[_]](value: Unit)(
        f: [a] => F1[a] => F2[P[a]]
    ): F2[Unit] = summon[Applicative[F2]].pure(() => ())
    override def foldr[F1[_], A2](value: Unit, init: A2)(
        f: [a] => (F1[a], A2) => A2
    ): A2 = init

  type Single[A1] = AList[[F[_]] =>> F[A1]]

  def single[A1]: Single[A1] = new Single[A1]:
    override def transform[F1[_], F2[_]](value: F1[A1])(f: [x] => F1[x] => F2[x]): F2[A1] =
      f(value)
    override def traverse[F1[_], F2[_]: Applicative](value: F1[A1])(
        f: [a] => F1[a] => F2[a]
    ): F2[A1] = f(value)
    override def traverseX[F1[_], F2[_]: Applicative, P[_]](value: F1[A1])(
        f: [a] => F1[a] => F2[P[a]]
    ): F2[P[A1]] = f(value)
    override def foldr[F1[_], A2](value: F1[A1], init: A2)(
        f: [a] => (F1[a], A2) => A2
    ): A2 = f(value, init)

  type ASplit[K1[F1[_]], F2[_]] = AList[SplitK[K1, F2]]
  def asplit[K1[g[_]], G2[_]](base: AList[K1]): ASplit[K1, G2] = new ASplit[K1, G2]:
    def transform[F1[_], F2[_]](value: SplitK[K1, G2][F1])(
        f: [a] => F1[a] => F2[a]
    ): SplitK[K1, G2][F2] =
      base.transform[Compose[F1, G2], Compose[F2, G2]](value) {
        nestCon[F1, F2, G2](f)
      }
    def traverse[F1[_], F2[_]: Applicative](value: SplitK[K1, G2][F1])(
        f: [a] => F1[a] => F2[a]
    ): F2[SplitK[K1, G2][Id]] = traverseX[F1, F2, Id](value)(f)

    def traverseX[F1[_], F2[_]: Applicative, P[_]](value: SplitK[K1, G2][F1])(
        f: [a] => F1[a] => F2[P[a]]
    ): F2[SplitK[K1, G2][P]] =
      base.traverseX[Compose[F1, G2], F2, Compose[P, G2]](value) {
        nestCon[F1, Compose[F2, P], G2](f)
      }
    def foldr[F1[_], A1](value: SplitK[K1, G2][F1], init: A1)(
        f: [a] => (F1[a], A1) => A1
    ): A1 = base.foldr[Compose[F1, G2], A1](value, init) {
      // This is safe because F1[G2[a]] is F1[a]
      f.asInstanceOf[[a] => (F1[G2[a]], A1) => A1]
    }

  type Tuple2K[A1, A2] = [F[_]] =>> Tuple.Map[(A1, A2), F]
  def tuple2[A1, A2]: AList[Tuple2K[A1, A2]] = tuple[(A1, A2)]
  type Tuple3K[A1, A2, A3] = [F[_]] =>> Tuple.Map[(A1, A2, A3), F]
  def tuple3[A1, A2, A3]: AList[Tuple3K[A1, A2, A3]] = tuple[(A1, A2, A3)]
  type Tuple4K[A1, A2, A3, A4] = [F[_]] =>> Tuple.Map[(A1, A2, A3, A4), F]
  def tuple4[A1, A2, A3, A4]: AList[Tuple4K[A1, A2, A3, A4]] = tuple[(A1, A2, A3, A4)]
  type Tuple5K[A1, A2, A3, A4, A5] = [F[_]] =>> Tuple.Map[(A1, A2, A3, A4, A5), F]
  def tuple5[A1, A2, A3, A4, A5]: AList[Tuple5K[A1, A2, A3, A4, A5]] = tuple[(A1, A2, A3, A4, A5)]
  type Tuple6K[A1, A2, A3, A4, A5, A6] = [F[_]] =>> Tuple.Map[(A1, A2, A3, A4, A5, A6), F]
  def tuple6[A1, A2, A3, A4, A5, A6]: AList[Tuple6K[A1, A2, A3, A4, A5, A6]] =
    tuple[(A1, A2, A3, A4, A5, A6)]
  type Tuple7K[A1, A2, A3, A4, A5, A6, A7] = [F[_]] =>> Tuple.Map[(A1, A2, A3, A4, A5, A6, A7), F]
  def tuple7[A1, A2, A3, A4, A5, A6, A7]: AList[Tuple7K[A1, A2, A3, A4, A5, A6, A7]] =
    tuple[(A1, A2, A3, A4, A5, A6, A7)]
  type Tuple8K[A1, A2, A3, A4, A5, A6, A7, A8] =
    [F[_]] =>> Tuple.Map[(A1, A2, A3, A4, A5, A6, A7, A8), F]
  def tuple8[A1, A2, A3, A4, A5, A6, A7, A8]: AList[Tuple8K[A1, A2, A3, A4, A5, A6, A7, A8]] =
    tuple[(A1, A2, A3, A4, A5, A6, A7, A8)]
  type Tuple9K[A1, A2, A3, A4, A5, A6, A7, A8, A9] =
    [F[_]] =>> Tuple.Map[(A1, A2, A3, A4, A5, A6, A7, A8, A9), F]
  def tuple9[A1, A2, A3, A4, A5, A6, A7, A8, A9]
      : AList[Tuple9K[A1, A2, A3, A4, A5, A6, A7, A8, A9]] =
    tuple[(A1, A2, A3, A4, A5, A6, A7, A8, A9)]
  type Tuple10K[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10] =
    [F[_]] =>> Tuple.Map[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10), F]
  def tuple10[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10]
      : AList[Tuple10K[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10]] =
    tuple[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10)]
  type Tuple11K[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11] =
    [F[_]] =>> Tuple.Map[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11), F]
  def tuple11[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11]
      : AList[Tuple11K[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11]] =
    tuple[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11)]

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
          case _: Tuple.Map[EmptyTuple, F1] =>
            F2.pure(() => nil[Tup].asInstanceOf[Tuple.Map[Tup, Id]])
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
          case _: Tuple.Map[EmptyTuple, F1] => F2.pure(() => nil[Tuple.Map[Tup, P]])
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

  def list[A]: AList[[F[_]] =>> List[F[A]]] =
    new AList[[F[_]] =>> List[F[A]]]:
      override def transform[F1[_], F2[_]](value: List[F1[A]])(
          f: [x] => F1[x] => F2[x]
      ): List[F2[A]] = value.map(f[A])

      override def mapN[F1[_]: Applicative, A1](value: List[F1[A]])(f: List[Id[A]] => A1): F1[A1] =
        val ap = summon[Applicative[F1]]
        def loop[V](in: List[F1[A]], g: List[A] => V): F1[V] =
          in match
            case Nil => ap.pure(() => g(Nil))
            case x :: xs =>
              val h = (ts: List[A]) => (t: A) => g(t :: ts)
              ap.ap(loop(xs, h))(x)
        loop(value, f)

      override def foldr[F1[_], A1](value: List[F1[A]], init: A1)(
          f: [a] => (F1[a], A1) => A1
      ): A1 = value.reverse.foldLeft(init)((t, m) => f(m, t))
      override def traverse[F1[_], F2[_]: Applicative](value: List[F1[A]])(
          f: [a] => F1[a] => F2[a]
      ): F2[List[Id[A]]] = ???

      override def traverseX[F1[_], F2[_]: Applicative, P[_]](value: List[F1[A]])(
          f: [a] => F1[a] => F2[P[a]]
      ): F2[List[P[A]]] = ???
end AList
