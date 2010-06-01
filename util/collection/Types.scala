/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

object Types extends TypeFunctions
{
	val :^: = MCons
	val :+: = HCons
	type :+:[H, T <: HList] = HCons[H,T]
}