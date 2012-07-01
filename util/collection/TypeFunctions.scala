/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

trait TypeFunctions
{
	type Id[X] = X
	sealed trait Const[A] { type Apply[B] = A }
	sealed trait Compose[A[_], B[_]] { type Apply[T] = A[B[T]] }
	sealed trait ∙[A[_], B[_]] { type l[T] = A[B[T]] }
	sealed trait P1of2[M[_,_], A] { type Apply[B] = M[A,B]; type Flip[B] = M[B, A] }

	final val left = new (Id ~> P1of2[Left, Nothing]#Flip) { def apply[T](t: T) = Left(t) }
	final val right = new (Id ~> P1of2[Right, Nothing]#Apply) { def apply[T](t: T) = Right(t) }
	final val some = new (Id ~> Some) { def apply[T](t: T) = Some(t) }
	final def idFun[T] = (t: T) => t
	final def const[A,B](b: B): A=> B = _ => b
	
	def nestCon[M[_], N[_], G[_]](f: M ~> N): (M ∙ G)#l ~> (N ∙ G)#l =
		f.asInstanceOf[(M ∙ G)#l ~> (N ∙ G)#l] // implemented with a cast to avoid extra object+method call.  castless version:
	/* new ( (M ∙ G)#l ~> (N ∙ G)#l ) {
		def apply[T](mg: M[G[T]]): N[G[T]] = f(mg)
	}*/

	implicit def toFn1[A,B](f: A => B): Fn1[A,B] = new Fn1[A,B] {
		def ∙[C](g: C => A) = f compose g
	}
	def idK[M[_]]: M ~> M = new (M ~> M) { def apply[T](m: M[T]): M[T] = m }
	
	type Endo[T] = T=>T
	type ~>|[A[_],B[_]] = A ~> Compose[Option, B]#Apply
}
object TypeFunctions extends TypeFunctions

trait ~>[-A[_], +B[_]]
{ outer =>
	def apply[T](a: A[T]): B[T]
	// directly on ~> because of type inference limitations
	final def ∙[C[_]](g: C ~> A): C ~> B = new (C ~> B) { def apply[T](c: C[T]) = outer.apply(g(c)) }
	final def ∙[C,D](g: C => D)(implicit ev: D <:< A[D]): C => B[D] = i => apply(ev(g(i)) )
	final def fn[T] = (t: A[T]) => apply[T](t)
}
object ~>
{
	import TypeFunctions._
	val Id: Id ~> Id = new (Id ~> Id) { def apply[T](a: T): T = a }
	implicit def tcIdEquals: (Id ~> Id) = Id
}
trait Fn1[A, B] {
	def ∙[C](g: C => A): C => B
}