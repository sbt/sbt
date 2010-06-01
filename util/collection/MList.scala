/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._

sealed trait MList[+M[_]]
{
	type Map[N[_]] <: MList[N]
	def map[N[_]](f: M ~> N): Map[N]

	def toList: List[M[_]]
}
final case class MCons[H, +T <: MList[M], +M[_]](head: M[H], tail: T) extends MList[M]
{
	type Map[N[_]] = MCons[H, tail.Map[N], N]
	def map[N[_]](f: M ~> N) = MCons( f(head), tail.map(f) )

	def :^: [N[X] >: M[X], G](g: N[G]): MCons[G, MCons[H, T, N], N] = MCons(g, this)

	def toList = head :: tail.toList
}
sealed class MNil extends MList[Nothing]
{
	type Map[N[_]] = MNil
	def map[N[_]](f: Nothing ~> N) = MNil

	def :^: [M[_], H](h: M[H]): MCons[H, MNil, M] = MCons(h, this)

	def toList = Nil
}
object MNil extends MNil
