/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
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
  def traverse[M[_], N[_], P[_]](value: K[M], f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[K[P]]
  def foldr[M[_], A](value: K[M], f: (M[_], A) => A, init: A): A

  def toList[M[_]](value: K[M]): List[M[_]] = foldr[M, List[M[_]]](value, _ :: _, Nil)

  def apply[M[_], C](value: K[M], f: K[Id] => C)(implicit a: Applicative[M]): M[C] =
    a.map(f, traverse[M, M, Id](value, idK[M])(a))
}

object AList {
  type Empty = AList[ConstK[Unit]#l]

  /** AList for Unit, which represents a sequence that is always empty.*/
  val empty: Empty = new Empty {
    def transform[M[_], N[_]](in: Unit, f: M ~> N) = ()
    def foldr[M[_], T](in: Unit, f: (M[_], T) => T, init: T) = init
    override def apply[M[_], C](in: Unit, f: Unit => C)(implicit app: Applicative[M]): M[C] = app.pure(f(()))
    def traverse[M[_], N[_], P[_]](in: Unit, f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[Unit] = np.pure(())
  }

  type SeqList[T] = AList[λ[L[x] => List[L[T]]]]

  /** AList for a homogeneous sequence. */
  def seq[T]: SeqList[T] = new SeqList[T] {
    def transform[M[_], N[_]](s: List[M[T]], f: M ~> N) = s.map(f.fn[T])
    def foldr[M[_], A](s: List[M[T]], f: (M[_], A) => A, init: A): A = (init /: s.reverse)((t, m) => f(m, t))

    override def apply[M[_], C](s: List[M[T]], f: List[T] => C)(implicit ap: Applicative[M]): M[C] = {
      def loop[V](in: List[M[T]], g: List[T] => V): M[V] =
        in match {
          case Nil => ap.pure(g(Nil))
          case x :: xs =>
            val h = (ts: List[T]) => (t: T) => g(t :: ts)
            ap.apply(loop(xs, h), x)
        }
      loop(s, f)
    }

    def traverse[M[_], N[_], P[_]](s: List[M[T]], f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[List[P[T]]] = ???
  }

  /** AList for the arbitrary arity data structure KList. */
  def klist[KL[M[_]] <: KList.Aux[M, KL]]: AList[KL] = new AList[KL] {
    def transform[M[_], N[_]](k: KL[M], f: M ~> N) = k.transform(f)
    def foldr[M[_], T](k: KL[M], f: (M[_], T) => T, init: T): T = k.foldr(f, init)
    override def apply[M[_], C](k: KL[M], f: KL[Id] => C)(implicit app: Applicative[M]): M[C] = k.apply(f)(app)
    def traverse[M[_], N[_], P[_]](k: KL[M], f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[KL[P]] = k.traverse[N, P](f)(np)
    override def toList[M[_]](k: KL[M]) = k.toList
  }

  type Single[A] = AList[λ[L[x] => L[A]]]

  /** AList for a single value. */
  def single[A]: Single[A] = new Single[A] {
    def transform[M[_], N[_]](a: M[A], f: M ~> N) = f(a)
    def foldr[M[_], T](a: M[A], f: (M[_], T) => T, init: T): T = f(a, init)
    def traverse[M[_], N[_], P[_]](a: M[A], f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[P[A]] = f(a)
  }

  type ASplit[K[L[x]], B[x]] = AList[λ[L[x] => K[(L ∙ B)#l]]]

  /** AList that operates on the outer type constructor `A` of a composition `[x] A[B[x]]` for type constructors `A` and `B`*/
  def asplit[K[L[x]], B[x]](base: AList[K]): ASplit[K, B] = new ASplit[K, B] {
    type Split[L[x]] = K[(L ∙ B)#l]

    def transform[M[_], N[_]](value: Split[M], f: M ~> N): Split[N] =
      base.transform[(M ∙ B)#l, (N ∙ B)#l](value, nestCon[M, N, B](f))

    def traverse[M[_], N[_], P[_]](value: Split[M], f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[Split[P]] = {
      val g = nestCon[M, (N ∙ P)#l, B](f)
      base.traverse[(M ∙ B)#l, N, (P ∙ B)#l](value, g)(np)
    }

    def foldr[M[_], A](value: Split[M], f: (M[_], A) => A, init: A): A =
      base.foldr[(M ∙ B)#l, A](value, f, init)
  }

  // TODO: auto-generate
  sealed trait T2K[A, B] { type l[L[x]] = (L[A], L[B]) }
  type T2List[A, B] = AList[T2K[A, B]#l]
  def tuple2[A, B]: T2List[A, B] = new T2List[A, B] {
    type T2[M[_]] = (M[A], M[B])
    def transform[M[_], N[_]](t: T2[M], f: M ~> N): T2[N] = (f(t._1), f(t._2))
    def foldr[M[_], T](t: T2[M], f: (M[_], T) => T, init: T): T = f(t._1, f(t._2, init))
    def traverse[M[_], N[_], P[_]](t: T2[M], f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[T2[P]] = {
      val g = (Tuple2.apply[P[A], P[B]] _).curried
      np.apply(np.map(g, f(t._1)), f(t._2))
    }
  }

  sealed trait T3K[A, B, C] { type l[L[x]] = (L[A], L[B], L[C]) }
  type T3List[A, B, C] = AList[T3K[A, B, C]#l]
  def tuple3[A, B, C]: T3List[A, B, C] = new T3List[A, B, C] {
    type T3[M[_]] = (M[A], M[B], M[C])
    def transform[M[_], N[_]](t: T3[M], f: M ~> N) = (f(t._1), f(t._2), f(t._3))
    def foldr[M[_], T](t: T3[M], f: (M[_], T) => T, init: T): T = f(t._1, f(t._2, f(t._3, init)))
    def traverse[M[_], N[_], P[_]](t: T3[M], f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[T3[P]] = {
      val g = (Tuple3.apply[P[A], P[B], P[C]] _).curried
      np.apply(np.apply(np.map(g, f(t._1)), f(t._2)), f(t._3))
    }
  }

  sealed trait T4K[A, B, C, D] { type l[L[x]] = (L[A], L[B], L[C], L[D]) }
  type T4List[A, B, C, D] = AList[T4K[A, B, C, D]#l]
  def tuple4[A, B, C, D]: T4List[A, B, C, D] = new T4List[A, B, C, D] {
    type T4[M[_]] = (M[A], M[B], M[C], M[D])
    def transform[M[_], N[_]](t: T4[M], f: M ~> N) = (f(t._1), f(t._2), f(t._3), f(t._4))
    def foldr[M[_], T](t: T4[M], f: (M[_], T) => T, init: T): T = f(t._1, f(t._2, f(t._3, f(t._4, init))))
    def traverse[M[_], N[_], P[_]](t: T4[M], f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[T4[P]] = {
      val g = (Tuple4.apply[P[A], P[B], P[C], P[D]] _).curried
      np.apply(np.apply(np.apply(np.map(g, f(t._1)), f(t._2)), f(t._3)), f(t._4))
    }
  }

  sealed trait T5K[A, B, C, D, E] { type l[L[x]] = (L[A], L[B], L[C], L[D], L[E]) }
  type T5List[A, B, C, D, E] = AList[T5K[A, B, C, D, E]#l]
  def tuple5[A, B, C, D, E]: T5List[A, B, C, D, E] = new T5List[A, B, C, D, E] {
    type T5[M[_]] = (M[A], M[B], M[C], M[D], M[E])
    def transform[M[_], N[_]](t: T5[M], f: M ~> N) = (f(t._1), f(t._2), f(t._3), f(t._4), f(t._5))
    def foldr[M[_], T](t: T5[M], f: (M[_], T) => T, init: T): T = f(t._1, f(t._2, f(t._3, f(t._4, f(t._5, init)))))
    def traverse[M[_], N[_], P[_]](t: T5[M], f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[T5[P]] = {
      val g = (Tuple5.apply[P[A], P[B], P[C], P[D], P[E]] _).curried
      np.apply(np.apply(np.apply(np.apply(np.map(g, f(t._1)), f(t._2)), f(t._3)), f(t._4)), f(t._5))
    }
  }

  sealed trait T6K[A, B, C, D, E, F] { type l[L[x]] = (L[A], L[B], L[C], L[D], L[E], L[F]) }
  type T6List[A, B, C, D, E, F] = AList[T6K[A, B, C, D, E, F]#l]
  def tuple6[A, B, C, D, E, F]: T6List[A, B, C, D, E, F] = new T6List[A, B, C, D, E, F] {
    type T6[M[_]] = (M[A], M[B], M[C], M[D], M[E], M[F])
    def transform[M[_], N[_]](t: T6[M], f: M ~> N) = (f(t._1), f(t._2), f(t._3), f(t._4), f(t._5), f(t._6))
    def foldr[M[_], T](t: T6[M], f: (M[_], T) => T, init: T): T = f(t._1, f(t._2, f(t._3, f(t._4, f(t._5, f(t._6, init))))))
    def traverse[M[_], N[_], P[_]](t: T6[M], f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[T6[P]] = {
      val g = (Tuple6.apply[P[A], P[B], P[C], P[D], P[E], P[F]] _).curried
      np.apply(np.apply(np.apply(np.apply(np.apply(np.map(g, f(t._1)), f(t._2)), f(t._3)), f(t._4)), f(t._5)), f(t._6))
    }
  }

  sealed trait T7K[A, B, C, D, E, F, G] { type l[L[x]] = (L[A], L[B], L[C], L[D], L[E], L[F], L[G]) }
  type T7List[A, B, C, D, E, F, G] = AList[T7K[A, B, C, D, E, F, G]#l]
  def tuple7[A, B, C, D, E, F, G]: T7List[A, B, C, D, E, F, G] = new T7List[A, B, C, D, E, F, G] {
    type T7[M[_]] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G])
    def transform[M[_], N[_]](t: T7[M], f: M ~> N) = (f(t._1), f(t._2), f(t._3), f(t._4), f(t._5), f(t._6), f(t._7))
    def foldr[M[_], T](t: T7[M], f: (M[_], T) => T, init: T): T = f(t._1, f(t._2, f(t._3, f(t._4, f(t._5, f(t._6, f(t._7, init)))))))
    def traverse[M[_], N[_], P[_]](t: T7[M], f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[T7[P]] = {
      val g = (Tuple7.apply[P[A], P[B], P[C], P[D], P[E], P[F], P[G]] _).curried
      np.apply(np.apply(np.apply(np.apply(np.apply(np.apply(np.map(g, f(t._1)), f(t._2)), f(t._3)), f(t._4)), f(t._5)), f(t._6)), f(t._7))
    }
  }

  sealed trait T8K[A, B, C, D, E, F, G, H] { type l[L[x]] = (L[A], L[B], L[C], L[D], L[E], L[F], L[G], L[H]) }
  type T8List[A, B, C, D, E, F, G, H] = AList[T8K[A, B, C, D, E, F, G, H]#l]
  def tuple8[A, B, C, D, E, F, G, H]: T8List[A, B, C, D, E, F, G, H] = new T8List[A, B, C, D, E, F, G, H] {
    type T8[M[_]] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H])
    def transform[M[_], N[_]](t: T8[M], f: M ~> N) = (f(t._1), f(t._2), f(t._3), f(t._4), f(t._5), f(t._6), f(t._7), f(t._8))
    def foldr[M[_], T](t: T8[M], f: (M[_], T) => T, init: T): T = f(t._1, f(t._2, f(t._3, f(t._4, f(t._5, f(t._6, f(t._7, f(t._8, init))))))))
    def traverse[M[_], N[_], P[_]](t: T8[M], f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[T8[P]] = {
      val g = (Tuple8.apply[P[A], P[B], P[C], P[D], P[E], P[F], P[G], P[H]] _).curried
      np.apply(np.apply(np.apply(np.apply(np.apply(np.apply(np.apply(np.map(g, f(t._1)), f(t._2)), f(t._3)), f(t._4)), f(t._5)), f(t._6)), f(t._7)), f(t._8))
    }
  }

  sealed trait T9K[A, B, C, D, E, F, G, H, I] { type l[L[x]] = (L[A], L[B], L[C], L[D], L[E], L[F], L[G], L[H], L[I]) }
  type T9List[A, B, C, D, E, F, G, H, I] = AList[T9K[A, B, C, D, E, F, G, H, I]#l]
  def tuple9[A, B, C, D, E, F, G, H, I]: T9List[A, B, C, D, E, F, G, H, I] = new T9List[A, B, C, D, E, F, G, H, I] {
    type T9[M[_]] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I])
    def transform[M[_], N[_]](t: T9[M], f: M ~> N) = (f(t._1), f(t._2), f(t._3), f(t._4), f(t._5), f(t._6), f(t._7), f(t._8), f(t._9))
    def foldr[M[_], T](t: T9[M], f: (M[_], T) => T, init: T): T = f(t._1, f(t._2, f(t._3, f(t._4, f(t._5, f(t._6, f(t._7, f(t._8, f(t._9, init)))))))))
    def traverse[M[_], N[_], P[_]](t: T9[M], f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[T9[P]] = {
      val g = (Tuple9.apply[P[A], P[B], P[C], P[D], P[E], P[F], P[G], P[H], P[I]] _).curried
      np.apply(np.apply(np.apply(np.apply(np.apply(np.apply(np.apply(np.apply(np.map(g, f(t._1)), f(t._2)), f(t._3)), f(t._4)), f(t._5)), f(t._6)), f(t._7)), f(t._8)), f(t._9))
    }
  }

  sealed trait T10K[A, B, C, D, E, F, G, H, I, J] { type l[L[x]] = (L[A], L[B], L[C], L[D], L[E], L[F], L[G], L[H], L[I], L[J]) }
  type T10List[A, B, C, D, E, F, G, H, I, J] = AList[T10K[A, B, C, D, E, F, G, H, I, J]#l]
  def tuple10[A, B, C, D, E, F, G, H, I, J]: T10List[A, B, C, D, E, F, G, H, I, J] = new T10List[A, B, C, D, E, F, G, H, I, J] {
    type T10[M[_]] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J])
    def transform[M[_], N[_]](t: T10[M], f: M ~> N) = (f(t._1), f(t._2), f(t._3), f(t._4), f(t._5), f(t._6), f(t._7), f(t._8), f(t._9), f(t._10))
    def foldr[M[_], T](t: T10[M], f: (M[_], T) => T, init: T): T = f(t._1, f(t._2, f(t._3, f(t._4, f(t._5, f(t._6, f(t._7, f(t._8, f(t._9, f(t._10, init))))))))))
    def traverse[M[_], N[_], P[_]](t: T10[M], f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[T10[P]] = {
      val g = (Tuple10.apply[P[A], P[B], P[C], P[D], P[E], P[F], P[G], P[H], P[I], P[J]] _).curried
      np.apply(np.apply(np.apply(np.apply(np.apply(np.apply(np.apply(np.apply(np.apply(np.map(g, f(t._1)), f(t._2)), f(t._3)), f(t._4)), f(t._5)), f(t._6)), f(t._7)), f(t._8)), f(t._9)), f(t._10))
    }
  }

  sealed trait T11K[A, B, C, D, E, F, G, H, I, J, K] { type l[L[x]] = (L[A], L[B], L[C], L[D], L[E], L[F], L[G], L[H], L[I], L[J], L[K]) }
  type T11List[A, B, C, D, E, F, G, H, I, J, K] = AList[T11K[A, B, C, D, E, F, G, H, I, J, K]#l]
  def tuple11[A, B, C, D, E, F, G, H, I, J, K]: T11List[A, B, C, D, E, F, G, H, I, J, K] = new T11List[A, B, C, D, E, F, G, H, I, J, K] {
    type T11[M[_]] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J], M[K])
    def transform[M[_], N[_]](t: T11[M], f: M ~> N) = (f(t._1), f(t._2), f(t._3), f(t._4), f(t._5), f(t._6), f(t._7), f(t._8), f(t._9), f(t._10), f(t._11))
    def foldr[M[_], T](t: T11[M], f: (M[_], T) => T, init: T): T = f(t._1, f(t._2, f(t._3, f(t._4, f(t._5, f(t._6, f(t._7, f(t._8, f(t._9, f(t._10, f(t._11, init)))))))))))
    def traverse[M[_], N[_], P[_]](t: T11[M], f: M ~> (N ∙ P)#l)(implicit np: Applicative[N]): N[T11[P]] = {
      val g = (Tuple11.apply[P[A], P[B], P[C], P[D], P[E], P[F], P[G], P[H], P[I], P[J], P[K]] _).curried
      np.apply(np.apply(np.apply(np.apply(np.apply(np.apply(np.apply(np.apply(np.apply(np.apply(np.map(g, f(t._1)), f(t._2)), f(t._3)), f(t._4)), f(t._5)), f(t._6)), f(t._7)), f(t._8)), f(t._9)), f(t._10)), f(t._11))
    }
  }
}
