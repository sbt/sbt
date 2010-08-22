/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

object Types extends TypeFunctions
{
	val :^: = KCons
	val :+: = HCons
	type :+:[H, T <: HList] = HCons[H,T]

	implicit def hconsToK[M[_], H, T <: HList](h: M[H] :+: T)(implicit mt: T => KList[M, T]): KList[M, H :+: T] =
		KCons[H, T, M](h.head, mt(h.tail) )
	implicit def hnilToK(hnil: HNil): KNil = KNil
}
