/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

import Types._
import Classes.Applicative

/** Heterogeneous list with each element having type M[T] for some type T.*/
sealed trait KList[+M[_]] {
  type Transform[N[_]] <: KList[N]

  /** Apply the natural transformation `f` to each element. */
  def transform[N[_]](f: M ~> N): Transform[N]

  /** Folds this list using a function that operates on the homogeneous type of the elements of this list. */
  def foldr[B](f: (M[_], B) => B, init: B): B = init // had trouble defining it in KNil

  /** Applies `f` to the elements of this list in the applicative functor defined by `ap`. */
  def apply[N[x] >: M[x], Z](f: Transform[Id] => Z)(implicit ap: Applicative[N]): N[Z]

  /** Equivalent to `transform(f) . apply(x => x)`, this is the essence of the iterator at the level of natural transformations.*/
  def traverse[N[_], P[_]](f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[Transform[P]]

  /** Discards the heterogeneous type information and constructs a plain List from this KList's elements. */
  def toList: List[M[_]]
}
object KList {
  type Aux[+M[_], Transform0[N[_]]] = KList[M] { type Transform[N[_]] = Transform0[N] }
}

final case class KCons[H, +T <: KList[M], +M[_]](head: M[H], tail: T) extends KList[M] {
  final type Transform[N[_]] = KCons[H, tail.Transform[N], N]

  def transform[N[_]](f: M ~> N) = KCons(f(head), tail.transform(f))
  def toList: List[M[_]] = head :: tail.toList

  def apply[N[x] >: M[x], Z](f: Transform[Id] => Z)(implicit ap: Applicative[N]): N[Z] = {
    val g = (t: tail.Transform[Id]) => (h: H) => f(KCons[H, tail.Transform[Id], Id](h, t))
    ap.apply(tail.apply[N, H => Z](g), head)
  }

  def traverse[N[_], P[_]](f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[Transform[P]] = {
    val tt: N[tail.Transform[P]] = tail.traverse[N, P](f)
    val g = (t: tail.Transform[P]) => (h: P[H]) => KCons(h, t)
    np.apply(np.map(g, tt), f(head))
  }

  def :^:[A, N[x] >: M[x]](h: N[A]) = KCons(h, this)
  override def foldr[B](f: (M[_], B) => B, init: B): B = f(head, tail.foldr(f, init))
}

sealed abstract class KNil extends KList[Nothing] {
  final type Transform[N[_]] = KNil
  final def transform[N[_]](f: Nothing ~> N): Transform[N] = KNil
  final def toList = Nil
  final def apply[N[x], Z](f: KNil => Z)(implicit ap: Applicative[N]): N[Z] = ap.pure(f(KNil))

  final def traverse[N[_], P[_]](f: Nothing ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[KNil] =
    np.pure(KNil)
}

case object KNil extends KNil {
  def :^:[M[_], H](h: M[H]): KCons[H, KNil, M] = KCons(h, this)
}
