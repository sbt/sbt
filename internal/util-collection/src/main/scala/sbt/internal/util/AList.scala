/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import Classes.Applicative
import Types._

/**
 * An abstraction over a higher-order type constructor `K[x[y]]` with the purpose of abstracting
 * over heterogeneous sequences like `KList` and `TupleN` with elements with a common type
 * constructor as well as homogeneous sequences `Seq[M[T]]`.
 */
trait AList[K[L[x]]] {
  def transform[M[_], N[_]](value: K[M], f: M ~> N): K[N]
  def traverse[M[_], N[_], P[_]](value: K[M], f: M ~> (N ∙ P)#l)(
      implicit np: Applicative[N]
  ): N[K[P]]
  def foldr[M[_], A](value: K[M], f: (M[_], A) => A, init: A): A

  def toList[M[_]](value: K[M]): List[M[_]] = foldr[M, List[M[_]]](value, _ :: _, Nil)

  def apply[M[_], C](value: K[M], f: K[Id] => C)(implicit a: Applicative[M]): M[C] =
    a.map(f, traverse[M, M, Id](value, idK[M])(a))
}

object AList extends AListArity {
  type Empty = AList[ConstK[Unit]#l]

  /** AList for Unit, which represents a sequence that is always empty.*/
  val empty: Empty = new Empty {
    def transform[M[_], N[_]](in: Unit, f: M ~> N) = ()
    def foldr[M[_], T](in: Unit, f: (M[_], T) => T, init: T) = init
    override def apply[M[_], C](in: Unit, f: Unit => C)(implicit app: Applicative[M]): M[C] =
      app.pure(f(()))
    def traverse[M[_], N[_], P[_]](in: Unit, f: M ~> (N ∙ P)#l)(
        implicit np: Applicative[N]
    ): N[Unit] = np.pure(())
  }

  type SeqList[T] = AList[λ[L[x] => List[L[T]]]]

  /** AList for a homogeneous sequence. */
  def seq[T]: SeqList[T] = new SeqList[T] {
    def transform[M[_], N[_]](s: List[M[T]], f: M ~> N) = s.map(f.fn[T])
    def foldr[M[_], A](s: List[M[T]], f: (M[_], A) => A, init: A): A =
      s.reverse.foldLeft(init)((t, m) => f(m, t))

    override def apply[M[_], C](s: List[M[T]], f: List[T] => C)(
        implicit ap: Applicative[M]
    ): M[C] = {
      def loop[V](in: List[M[T]], g: List[T] => V): M[V] =
        in match {
          case Nil => ap.pure(g(Nil))
          case x :: xs =>
            val h = (ts: List[T]) => (t: T) => g(t :: ts)
            ap.apply(loop(xs, h), x)
        }
      loop(s, f)
    }

    def traverse[M[_], N[_], P[_]](s: List[M[T]], f: M ~> (N ∙ P)#l)(
        implicit np: Applicative[N]
    ): N[List[P[T]]] = ???
  }

  /** AList for the arbitrary arity data structure KList. */
  def klist[KL[M[_]] <: KList.Aux[M, KL]]: AList[KL] = new AList[KL] {
    def transform[M[_], N[_]](k: KL[M], f: M ~> N) = k.transform(f)
    def foldr[M[_], T](k: KL[M], f: (M[_], T) => T, init: T): T = k.foldr(f, init)
    override def apply[M[_], C](k: KL[M], f: KL[Id] => C)(implicit app: Applicative[M]): M[C] =
      k.apply(f)(app)
    def traverse[M[_], N[_], P[_]](k: KL[M], f: M ~> (N ∙ P)#l)(
        implicit np: Applicative[N]
    ): N[KL[P]] = k.traverse[N, P](f)(np)
    override def toList[M[_]](k: KL[M]) = k.toList
  }

  type Single[A] = AList[λ[L[x] => L[A]]]

  /** AList for a single value. */
  def single[A]: Single[A] = new Single[A] {
    def transform[M[_], N[_]](a: M[A], f: M ~> N) = f(a)
    def foldr[M[_], T](a: M[A], f: (M[_], T) => T, init: T): T = f(a, init)
    def traverse[M[_], N[_], P[_]](a: M[A], f: M ~> (N ∙ P)#l)(
        implicit np: Applicative[N]
    ): N[P[A]] = f(a)
  }

  type ASplit[K[L[x]], B[x]] = AList[λ[L[x] => K[(L ∙ B)#l]]]

  /** AList that operates on the outer type constructor `A` of a composition `[x] A[B[x]]` for type constructors `A` and `B`*/
  def asplit[K[L[x]], B[x]](base: AList[K]): ASplit[K, B] = new ASplit[K, B] {
    type Split[L[x]] = K[(L ∙ B)#l]

    def transform[M[_], N[_]](value: Split[M], f: M ~> N): Split[N] =
      base.transform[(M ∙ B)#l, (N ∙ B)#l](value, nestCon[M, N, B](f))

    def traverse[M[_], N[_], P[_]](value: Split[M], f: M ~> (N ∙ P)#l)(
        implicit np: Applicative[N]
    ): N[Split[P]] = {
      val g = nestCon[M, (N ∙ P)#l, B](f)
      base.traverse[(M ∙ B)#l, N, (P ∙ B)#l](value, g)(np)
    }

    def foldr[M[_], A](value: Split[M], f: (M[_], A) => A, init: A): A =
      base.foldr[(M ∙ B)#l, A](value, f, init)
  }
}
