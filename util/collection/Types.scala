package sbt

object Types extends TypeFunctions
{
	val :^: = MCons
	val :+: = HCons
	type :+:[H, T <: HList] = HCons[H,T]
}