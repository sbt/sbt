package sbt

import Types._

sealed trait MList[M[_]]
{
	type Map[N[_]] <: MList[N]
	def map[N[_]](f: M ~> N): Map[N]

	type Down <: HList
	def down: Down

	def toList: List[M[_]]
}
final case class MCons[H, T <: MList[M], M[_]](head: M[H], tail: T) extends MList[M]
{
	type Down = M[H] :+: T#Down
	def down = HCons(head, tail.down)

	type Map[N[_]] = MCons[H, T#Map[N], N]
	def map[N[_]](f: M ~> N) = MCons( f(head), tail.map(f) )

	def :^: [G](g: M[G]): MCons[G, MCons[H, T, M], M] = MCons(g, this)

	def toList = head :: tail.toList
}
sealed class MNil[M[_]] extends MList[M]
{
	type Down = HNil
	def down = HNil
	
	type Map[N[_]] = MNil[N]
	def map[N[_]](f: M ~> N): MNil[N] = new MNil[N]

	def :^: [H](h: M[H]): MCons[H, MNil[M], M] = MCons(h, this)

	def toList = Nil
}
object MNil extends MNil[Id]
{
	implicit def apply[N[_]]: MNil[N] = new MNil[N]
}