/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._

sealed trait HList
{
	type ToM[M[_]] <: MList[M]
	type Up <: MList[Id]
	def up: Up
}
sealed trait HNil extends HList
{
	type ToM[M[_]] = MNil
	type Up = MNil
	def up = MNil
	def :+: [G](g: G): G :+: HNil = HCons(g, this)
}
object HNil extends HNil
final case class HCons[H, T <: HList](head : H, tail : T) extends HList
{
	type ToM[M[_]] = MCons[H, tail.ToM[M], M]
	type Up = MCons[H, tail.Up, Id]
	def up = MCons[H,tail.Up, Id](head, tail.up)
	def :+: [G](g: G): G :+: H :+: T = HCons(g, this)
}

object HList
{
	// contains no type information: not even A
	implicit def fromList[A](list: Traversable[A]): HList = ((HNil: HList) /: list) ( (hl,v) => HCons(v, hl) )
}