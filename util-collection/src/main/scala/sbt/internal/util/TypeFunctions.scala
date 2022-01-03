/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

trait TypeFunctions:
  type Id[X] = X
  type NothingK[X] = Nothing

  /*
  import TypeFunctions._
  sealed trait Const[A] { type Apply[B] = A }
  sealed trait ConstK[A] { type l[L[x]] = A }
   */
  sealed trait Compose[A[_], B[_]] { type Apply[T] = A[B[T]] }

  sealed trait ∙[A[_], B[_]] { type l[T] = A[B[T]] }
  private type AnyLeft[T] = Left[T, Nothing]
  private type AnyRight[T] = Right[Nothing, T]
  final val left: [A] => A => Left[A, Nothing] = [A] => (a: A) => Left(a)

  final val right: [A] => A => Right[Nothing, A] = [A] => (a: A) => Right(a)

  final val some: [A] => A => Some[A] = [A] => (a: A) => Some(a)
  // Id ~> Left[*, Nothing] =
  // λ[Id ~> AnyLeft](Left(_)).setToString("TypeFunctions.left")
  // final val right: Id ~> Right[Nothing, *] =
  //   λ[Id ~> AnyRight](Right(_)).setToString("TypeFunctions.right")
  // final val some: Id ~> Some[*] = λ[Id ~> Some](Some(_)).setToString("TypeFunctions.some")

  final def idFun[A]: A => A = ((a: A) => a) // .setToString("TypeFunctions.id")
  final def const[A, B](b: B): A => B = ((_: A) => b) // .setToString(s"TypeFunctions.const($b)")
  /*
  final def idK[M[_]]: M ~> M = λ[M ~> M](m => m).setToString("TypeFunctions.idK")

  def nestCon[M[_], N[_], G[_]](f: M ~> N): (M ∙ G)#l ~> (N ∙ G)#l =
    f.asInstanceOf[(M ∙ G)#l ~> (N ∙ G)#l] // implemented with a cast to avoid extra object+method call.
  // castless version:
  // λ[(M ∙ G)#l ~> (N ∙ G)#l](f(_))

  type Endo[T] = T => T
  type ~>|[A[_], B[_]] = A ~> Compose[Option, B]#Apply
   */
  type ~>|[F1[_], F2[_]] = [A] => F1[A] => Option[F2[A]]

end TypeFunctions

/*
object TypeFunctions extends TypeFunctions:

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

end TypeFunctions
 */

trait ~>[-F1[_], +F2[_]] { outer =>
  def apply[A](f1: F1[A]): F2[A]
  // directly on ~> because of type inference limitations
  final def ∙[F3[_]](g: F3 ~> F1): F3 ~> F2 = new ~>[F3, F2] {
    override def apply[A](f3: F3[A]) = outer.apply(g(f3))
  }
  final def ∙[C, D](g: C => D)(implicit ev: D <:< F1[D]): C => F2[D] = i => apply(ev(g(i)))
  lazy val fn: [A] => F1[A] => F2[A] = [A] => (f1: F1[A]) => outer.apply[A](f1)
}

/*
object ~> {
  import TypeFunctions._
  val Id: Id ~> Id = idK[Id]
  implicit def tcIdEquals: Id ~> Id = Id
}
 */
