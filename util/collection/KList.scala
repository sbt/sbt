/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._

/** A higher-order heterogeneous list.  It has a type constructor M[_] and
* type parameters HL.  The underlying data is M applied to each type parameter.
* Explicitly tracking M[_] allows performing natural transformations or ensuring
* all data conforms to some common type.
*
* For background, see
* http://apocalisp.wordpress.com/2010/11/01/type-level-programming-in-scala-part-8a-klist%C2%A0motivation/
 */
sealed trait KList[M[_], HL <: HList]
{
	type Raw = HL
	/** Transform to the underlying HList type.*/
	def down(implicit ev: M ~> Id): HL
	/** Apply a natural transformation. */
	def transform[N[_]](f: M ~> N): KList[N, HL]
	/** Convert to a List. */
	def toList: List[M[_]]
	/** Convert to an HList. */
	def combine[N[X] >: M[X]]: HL#Wrap[N]

	def foldr[P[_ <: HList],N[X] >: M[X]](f: KFold[N,P]): P[HL]
}
trait KFold[M[_],P[_ <: HList]]
{
	def kcons[H,T <: HList](h: M[H], acc: P[T]): P[H :+: T]
	def knil: P[HNil]
}

final case class KCons[H, T <: HList, M[_]](head: M[H], tail: KList[M,T]) extends KList[M, H :+: T]
{
	def down(implicit f: M ~> Id) = HCons(f(head), tail down f)
	def transform[N[_]](f: M ~> N) = KCons( f(head), tail transform f )
	// prepend
	def :^: [G](g: M[G]) = KCons(g, this)
	def toList = head :: tail.toList
	
	def combine[N[X] >: M[X]]: (H :+: T)#Wrap[N] = HCons(head, tail.combine)

	override def toString = head + " :^: " + tail.toString

	def foldr[P[_ <: HList],N[X] >: M[X]](f: KFold[N,P]) = f.kcons(head, tail foldr f)
}

sealed case class KNil[M[_]]() extends KList[M, HNil]
{
	def down(implicit f: M ~> Id) = HNil
	def transform[N[_]](f: M ~> N) = new KNil[N]
	def toList = Nil
	def combine[N[X]] = HNil
	override def foldr[P[_ <: HList],N[_]](f: KFold[N,P]) = f.knil
	override def toString = "KNil"
}
object KNil
{
	def :^: [M[_], H](h: M[H]) = KCons(h, new KNil[M])
}
object KList
{
	implicit def convert[M[_]](k: KNil.type): KNil[M] = KNil()
	// nicer alias for pattern matching
	val :^: = KCons
	
	def fromList[M[_]](s: Seq[M[_]]): KList[M, _ <: HList] = if(s.isEmpty) KNil() else KCons(s.head, fromList(s.tail))

	// haven't found a way to convince scalac that KList[M, H :+: T] implies KCons[H,T,M]
	// Therefore, this method exists to put the cast in one location.
	implicit def kcons[H, T <: HList, M[_]](kl: KList[M, H :+: T]): KCons[H,T,M] =
		kl.asInstanceOf[KCons[H,T,M]]
	// haven't need this, but for symmetry with kcons:
	implicit def knil[M[_]](kl: KList[M, HNil]): KNil[M] = KNil()
}
