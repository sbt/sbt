/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

trait TypeFunctions {
  import TypeFunctions._
  type Id[X] = X
  type NothingK[X] = Nothing
  sealed trait Const[A] { type Apply[B] = A }
  sealed trait ConstK[A] { type l[L[x]] = A }
  sealed trait Compose[A[_], B[_]] { type Apply[T] = A[B[T]] }
  sealed trait ∙[A[_], B[_]] { type l[T] = A[B[T]] }
  private type AnyLeft[T] = Left[T, Nothing]
  private type AnyRight[T] = Right[Nothing, T]

  final val left: Id ~> Left[*, Nothing] =
    λ[Id ~> AnyLeft](Left(_)).setToString("TypeFunctions.left")
  final val right: Id ~> Right[Nothing, *] =
    λ[Id ~> AnyRight](Right(_)).setToString("TypeFunctions.right")
  final val some: Id ~> Some[*] = λ[Id ~> Some](Some(_)).setToString("TypeFunctions.some")
  final def idFun[T]: T => T = ((t: T) => t).setToString("TypeFunctions.id")
  final def const[A, B](b: B): A => B = ((_: A) => b).setToString(s"TypeFunctions.const($b)")
  final def idK[M[_]]: M ~> M = λ[M ~> M](m => m).setToString("TypeFunctions.idK")

  def nestCon[M[_], N[_], G[_]](f: M ~> N): (M ∙ G)#l ~> (N ∙ G)#l =
    f.asInstanceOf[(M ∙ G)#l ~> (N ∙ G)#l] // implemented with a cast to avoid extra object+method call.
  // castless version:
  // λ[(M ∙ G)#l ~> (N ∙ G)#l](f(_))

  type Endo[T] = T => T
  type ~>|[A[_], B[_]] = A ~> Compose[Option, B]#Apply
}

object TypeFunctions extends TypeFunctions {
  private implicit class Ops[T[_], R[_]](val underlying: T ~> R) extends AnyVal {
    def setToString(string: String): T ~> R = new (T ~> R) {
      override def apply[U](a: T[U]): R[U] = underlying(a)
      override def toString: String = string
      override def equals(o: Any): Boolean = underlying.equals(o)
      override def hashCode: Int = underlying.hashCode
    }
  }
  private implicit class FunctionOps[A, B](val f: A => B) extends AnyVal {
    def setToString(string: String): A => B = new (A => B) {
      override def apply(a: A): B = f(a)
      override def toString: String = string
      override def equals(o: Any): Boolean = f.equals(o)
      override def hashCode: Int = f.hashCode
    }
  }
}

trait ~>[-A[_], +B[_]] { outer =>
  def apply[T](a: A[T]): B[T]
  // directly on ~> because of type inference limitations
  final def ∙[C[_]](g: C ~> A): C ~> B = λ[C ~> B](c => outer.apply(g(c)))
  final def ∙[C, D](g: C => D)(implicit ev: D <:< A[D]): C => B[D] = i => apply(ev(g(i)))
  final def fn[T]: A[T] => B[T] = (t: A[T]) => apply[T](t)
}

object ~> {
  import TypeFunctions._
  val Id: Id ~> Id = idK[Id]
  implicit def tcIdEquals: Id ~> Id = Id
}
