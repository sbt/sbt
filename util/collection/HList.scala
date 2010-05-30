package sbt

import Types._

sealed trait HList
{
	type Up <: MList[Id]
	def up: Up
}
sealed trait HNil extends HList
{
	type Up = MNil[Id]
	def up = MNil
	def :+: [G](g: G): G :+: HNil = HCons(g, this)
}
object HNil extends HNil
final case class HCons[H, T <: HList](head : H, tail : T) extends HList
{
	type Up = MCons[H, T#Up, Id]
	def up = MCons[H,T#Up, Id](head, tail.up)
	def :+: [G](g: G): G :+: H :+: T = HCons(g, this)
}